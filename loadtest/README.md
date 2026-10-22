# 부하 테스트

시나리오는 두 묶음이다.
- **D0~D6 — 1차 배포(택시 꺼짐, `TAXI_ENABLED=false`)**: 매칭 + 채팅만. 지금 쓰는 것.
- **S1~S6 — 택시 켜짐(발표 모드)**: 기사 위치·배차 포함. 택시 기능을 켤 때 다시 쓴다.

## 준비
1. 대상 서버: `micrometer-registry-prometheus` 추가, `exposure.include` 에 `prometheus`, prod 는 `management.server.port: 9091` (CloudFront VPC origin 이 8080 을 통째로 전달하므로 관리 포트를 분리해야 인터넷에 안 노출됨). actuator base-path 가 `/` 라 지표는 `http://IP:9091/prometheus`.
2. k6 서버: `infra/monitoring/prometheus.yml` 의 대상 IP 확인(dev 서버 A 172.16.1.30, C 172.16.1.46) → 레포 루트에서 `docker compose -f infra/monitoring/docker-compose.yml up -d`
   Grafana(3000) 데이터소스에 `http://prometheus:9090` 추가. 대시보드는 Spring Boot(ID 19004)·k6(ID 18030) 템플릿 import.
3. DB 는 Neon `loadtest` 브랜치로 전환 (서버 env 의 `DATABASE_HOST` 만 교체 → 끝나면 `load-env.sh` 로 원복 후 브랜치 삭제).
   D6 를 돌릴 거면 같은 env 파일에 `JWT_ACCESS_VALIDITY=2h` 도 임시로 넣는다 (기본 30분이면 도중에 토큰 만료).
4. `curl $BASE_URL/api/v1/config` → `{"taxiEnabled":false}` 인지 확인. `true` 면 D 시나리오는 의미가 없다.
5. 사용자 생성: `BASE_URL=http://TARGET:8080 USERS=350 node seed.mjs` → `users.json`
   속도 조절: `PARALLEL`(동시 최대, 기본 10), `RATE`(초당 출발 인원), `DURATION_SEC`(전체를 이 시간에 맞춤). 예) `USERS=4000 DURATION_SEC=300 PARALLEL=40` 이면 약 5분. 한 명이 실패해도 멈추지 않고 성공한 사람만 번호순으로 저장한다
   토큰 유효 30분. 지나면 다시 seed (가입은 409 로 건너뛰고 로그인만 한다 — 같은 `RUN_ID` 를 줄 것).
6. **SSE 시나리오(D1-SSE·D2 `USE_SSE=1`·D7)는 xk6-sse 확장이 든 k6 가 필요하다.** 자동 프로비저닝 카탈로그에 없어 직접 빌드한다 (k6 서버, Docker 만 있으면 됨. Go 불필요):
   ```
   mkdir -p ~/k6-sse && cd ~/k6-sse
   docker run --rm -v "$PWD:/xk6" grafana/xk6 build v1.5.0 --with github.com/phymbert/xk6-sse@v0.2.0
   ./k6 version        # k6 v1.5.0 … 이 바이너리로 SSE 스크립트를 돌린다. 나머지 스크립트도 이걸로 돌려도 된다
   ```
   `-o experimental-prometheus-rw` 는 v1.5.0 에서도 같은 이름이다. 맥에서 검증하려면 `-e GOOS=darwin -e GOARCH=arm64` 를 docker run 에 붙인다.

## 1차 배포 시나리오 (D)

앱(frontend develop)의 화면별 호출을 그대로 따른다.

| 화면 | 앱이 하는 일 |
|---|---|
| 앱 시작 | `/config` · `/local/users/info` · `/users/me/favorite-places` · `/chat-rooms/me`(진행 중인 방 찾기) 각 1회 |
| 17 합승 탭 | 목록 즉시 + **4초** 폴링 (카메라가 멈추면 400ms 뒤 재조회) |
| 방 만들기 | 즐겨찾기 → 경로 미리보기(`POST /matching/routes`) → 방 생성. 장소 검색(카카오)은 부하에서 뺀다 |
| 참여 | 상세 1회(참여 확인 화면) → join |
| 21 대기 (폴링, SSE 전) | 상세 즉시 + **4초** 폴링. 1차 배포에선 `COMPLETED` 에서도 계속 돈다(`FINISHED` 까지) |
| 21 대기 (SSE 후) | `GET /rooms/{id}/events` 연결 1개 유지. `connected`(방 상세 전체, 재연결도 같다)로 화면을 그리고, `member-joined`·`member-left`(변경분)는 `version` 이 하나씩 이어질 때만 들고 있는 멤버 목록에 더하고 뺀다. 번호가 빠지면 상세를 1회 다시 읽는다(resync, 아래 SSE 정합성). `closed` 면 홈. 서버가 30초마다 `:ping` 주석 |
| 채팅 (폴링, SSE 전) | 채팅 탭 `/chat-rooms/me` → 방 열기 3건(첫 페이지·참여자·방 정보) → 소켓 + 읽음(소켓) + 폴링. **21 의 4초 폴링은 채팅 화면 아래에서 계속 돈다** |
| 채팅 (SSE 후) | 위와 같되 방 상세 4초 폴링이 없다. 방 변화는 SSE 변경분(`member-joined`·`member-left`·`closed`)으로만 받는다. 상태 폴링·지문 비교 없음(`/rooms/{id}/status` 는 서버에서 제거). 상세는 resync 때만 다시 읽는다 |
| 채팅 폴링 | 소켓으로 메시지를 **한 번이라도 받기 전엔 3초**, 받은 뒤엔 20초. 커서가 없으면 첫 페이지를 다시 읽는다 (`after?cursor=0` 은 서버가 400) |

1차 배포에선 `/chat-rooms/me` 10초 폴링이 **돌지 않는다**(앱이 대기 단계에선 건너뛴다). 그래서 21 화면의 「채팅 열기」 버튼도 뜨지 않고, 채팅은 채팅 탭으로 들어간다.

### 방의 위치 - 한 점에 몰지 않는다

방은 전국 21개 도시 100곳(`lib/api.js` 의 `출발지들`)에 조 번호로 나눠 만든다. 참여자는 앱처럼 **자기 출발지 주변 약 ±500m**(`viewportAround`)만 조회한다. 목적지는 같은 도시의 다음 출발지다(평균 직선 4km) - 부산에서 판교로 가는 400km 경로가 생기지 않게 한다.

- 전부 강남역 한 점에 만들면 뷰포트가 아무것도 걸러내지 못해 ACTIVE 방 전체가 목록 응답에 실린다. 5,000명(1,666조)에서 목록 조회가 10ms → 2초로 늘고 DB CPU 가 100% 가 됐다. 100곳으로 나누면 한 응답에 약 17개(1/100)만 들어온다.
- 무작위 좌표를 쓰지 않는 이유는 경로 캐시다. 캐시 키가 좌표(소수 5자리)라 좌표가 다르면 방마다 네이버를 부른다. 정해진 100곳이면 경로도 100개로 끝난다(TTL 10분마다 최대 100회).
- 채팅방은 `/chat-rooms/me` 의 첫 항목이 아니라 **이번 방의 목적지 이름**으로 찾는다. 입장이 늦을 때 지난 반복의 옛 방에 붙어 체크가 거짓 통과하는 것을 막는다.

| 환경변수 | 기본 | 뜻 |
|---|---|---|
| `SPOTS` | 100 | 쓸 출발지 수. 도시를 번갈아 가며 앞에서부터 쓴다. `1` 이면 예전처럼 전부 강남역 → 판교역(분산 전 결과와 비교할 때만) |
| `VIEW_DEG` | 0.005 | 조회 뷰포트 반경(도). 약 500m |
| `CHECK_SPOTS` | - | `1` 이면 `d0-smoke.js` 가 출발지 전부의 경로가 구해지는지 확인한다. 좌표를 바꾼 뒤 한 번만 |
| `CHAT_TAB_DELAY_SEC` | 2 | 방이 찬 걸 보고 채팅 탭으로 넘어가기까지의 시간. 그 뒤 `/chat-rooms/me` 를 **한 번만** 부르고, 이번 방의 채팅방이 없으면 실패로 남기고 채팅을 건너뛴다 |

**돌리기 전에 지난 실행의 잔여를 지운다.** 포기·중단으로 남은 방과 미완료 이벤트가 있으면 시작부터 결과가 오염된다. 운영 EC2 의 psql 에서 [`sql/reset.sql`](sql/reset.sql) 을 붙여 넣는다.

- `COMPLETED` 로 남은 방을 빼먹으면 그 멤버가 409 로 새 방을 못 만든다. 방 생성의 대부분이 거절되고 서버는 한가해 보이지만 여정은 돌지 않은 실행이 된다
- 방이 끝나도 채팅방에는 남아 있어 반복할수록 `/chat-rooms/me` 가 길어진다. 테스트 사용자를 옛 채팅방에서 내보낸다
- 실행을 중간에 끊었다면 반드시 다시 정리한다

### 비동기 입장이 제때 되는지 - 탐침과 사후 대조

방에 참여하면 채팅방 입장은 커밋 뒤에 리스너가 따로 처리한다. 이게 밀리면 사용자는 채팅 탭에서 방을 못 본다.

**여정은 앱처럼 `/chat-rooms/me` 를 한 번만 부른다.** 전원이 생길 때까지 다시 두드리면 확인 자체가 부하가 된다(1초 x 10번일 때 5,000명에서 초당 720건, 전체 요청의 40%). 그래서 확인은 부하와 떼어 두 가지로 한다.

| | 무엇을 | 어떻게 |
|---|---|---|
| 탐침 `chat_join_lag_ms` | **얼마나 늦었나** (표본) | 앞쪽 `PROBE_GROUPS` 개 조(기본 10조 = 30명)만 방 생성·참여 직후 채팅방이 뜰 때까지 잰다. 호출은 `GET /chat-rooms/me (probe)` 로 따로 잡힌다. 끝내 안 뜨면 한도(`PROBE_WAIT_SEC`, 60초)를 기록하고 `chat_join_lag_timeout` 을 올린다 |
| 사후 대조 [`sql/verify.sql`](sql/verify.sql) | **몇 건이 끝내 안 됐나** (전수) | 실행 뒤 "방 멤버 수 = 채팅방 멤버 수" 를 DB 로 맞춘다. `missing_members` 가 0 이어야 한다 |

D2 의 임계값은 `chat_join_lag_ms p(95) < 2s` 다 - 채팅 탭으로 넘어가는 2초(`CHAT_TAB_DELAY_SEC`) 안에 입장이 끝나야 사용자가 방을 본다. 탐침은 여정을 그대로 돌면서 재기만 하므로 부하 모양은 바뀌지 않는다. `PROBE_GROUPS=0` 이면 끈다.

화면 비중을 합승 탭 40% · 대기 30% · 채팅 30% 로 잡으면 **1인당 약 0.27 RPS** (상세 55% · 목록 36% · 채팅 폴링 6% · 기타 3%) → 동시 접속 100명 = 27 RPS + WebSocket 30연결.

```
export BASE_URL=http://internal-moyeota-dev-alb-1803675880.ap-northeast-2.elb.amazonaws.com   # 내부 ALB → dev 서버 A·C. 서버 한 대만 때리려면 http://172.16.1.30:8080
export K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write
k6 run d0-smoke.js                                                        # 여정 1회 - 반드시 먼저
k6 run -e CHECK_SPOTS=1 d0-smoke.js                                       # 출발지 100곳 경로 확인 - 좌표를 바꿨을 때만
k6 run d3-join-consistency.js                                             # 정합성 - 방마다 2명만 성공 + 채팅방 멤버 = 방 멤버
k6 run -o experimental-prometheus-rw d5-breakpoint.js                     # 한계 찾기 (10 → 200 RPS)
k6 run -o experimental-prometheus-rw -e CONCURRENT=100 d1-polling.js      # 읽기 폴링 5분
k6 run -o experimental-prometheus-rw -e VUS=90 d2-journey.js              # 전체 여정(메인) 9분
k6 run -o experimental-prometheus-rw -e CONNS=150 d4-chat-ws.js           # 채팅 WebSocket
k6 run -o experimental-prometheus-rw -e CONNS=150 -e MODE=fallback d4-chat-ws.js   # 소켓이 죽어 전원 3초 폴링으로 떨어진 상황
k6 run -o experimental-prometheus-rw -e VUS=30 d6-soak.js                 # 45분 지속 + 방치된 방 자동 종료

# ── SSE 전환 후 (xk6-sse 바이너리로) ──
./k6 run -o experimental-prometheus-rw -e CONCURRENT=100 d1-polling-sse.js         # D1 과 같은 100명, 요청은 절반·연결 60개
./k6 run -o experimental-prometheus-rw -e VUS=90 -e USE_SSE=1 --summary-export=results/d2-sse-90.json d2-journey.js   # 대기를 SSE 로, 채팅 중에는 방 호출 없음. 요청 수·sse_connect_ms·sse_errors 를 본다
./k6 run -o experimental-prometheus-rw -e CONNS=300 d7-sse-connections.js          # 연결 100→200→300 + 초당 2건 나갔다 들어오기(member-left·member-joined) → settle 뒤 화면 vs 상세 비교. 그동안 측정 방 5개에서 전파 지연(sse_propagation_ms)
```

| # | 확인하는 것 | 합격선 |
|---|---|---|
| D0 | 여정이 끝까지 도는가 (config → 생성 → 참여 → COMPLETED → 채팅 → 종료) | 체크 100% |
| D1 | 폴링이 목표 RPS 를 버티는가 | 요청별 p95 < 300ms, 실패 < 1% |
| D2 | 실제 비율로 섞였을 때 | p95 < 500ms, 채팅 전달 p95 < 500ms, 409 외 참여 실패 0 |
| D3 | 정원 초과 없음 + 채팅방 입장·퇴장이 방 멤버와 일치. 거절은 `PARTY_FULL` 또는 `PARTY_CLOSED` | 체크 100%, 5xx 0건, 끝난 뒤 `event_publication` 0행 |
| D4 | 동시 연결 한계·전달 지연 / fallback 모드의 폴링 폭증 | 전달 p95 < 500ms, STOMP 에러 0 |
| D5 | 어디서 꺾이는가 | 기준 없음 - `hikaricp_connections_pending`(DB) vs `system_cpu_usage`(앱) |
| D6 | 메모리·CPU 크레딧, 방치된 방이 30~40분 안에 FINISHED 되고 멤버가 풀려나는가 | 자동 종료 ≤ 42분 |
| D1-SSE | D1 과 같은 동시 접속에서 요청 수·연결 수·힙이 어떻게 바뀌나 (전후 비교) | 요청별 p95 < 300ms, `sse_connect_ms` p95 < 1s, `sse_errors` 0 |
| D2 `USE_SSE=1` | 대기를 SSE 로 바꿨을 때 전체 여정의 요청 수·지연이 폴링 D2 대비 어떻게 바뀌나. 변경분만으로 그린 화면이 맞는가(`sse_view_check`), 번호가 빠져 상세를 다시 읽었나(`sse_resync`), 신호를 놓쳐 포기했나(`journey_give_up{cause:missed-event}`). 반영 지연 지표는 제거됐다(아래 주의) | D2 와 같음 (p95 < 500ms, 채팅 전달 p95 < 500ms). 종료 확인 때 이미 비어 닫힌 방(CANCELED)은 구독이 403 이라 `sse_errors` 에 잡힌다 |
| D7 | SSE 동시 연결 한계. `process_open_fds`·힙·`tomcat_threads_busy`(연결이 스레드를 안 잡는지). churn 을 받은 화면이 settle 뒤 상세와 맞는가(`sse_view_check`), resync 가 얼마나 나나(`sse_resync`). 부하가 걸린 동안 전파 지연(`sse_propagation_ms`, 아래 전파 지연) | 연결 300 에서 `sse_connect_ms` p95 < 1s 유지, `sse_errors` 0 |

순서: D0 → D3 → D5 로 천장 → 그 60~70% 로 D1·D2 → D4 → D6.
SSE 배포 뒤: 폴링 D1 결과를 baseline 으로 두고 → D1-SSE → D2 `USE_SSE=1` → D7. 같은 `CONCURRENT`·`VUS` 로 돌려야 비교가 된다.

### SSE 정합성 - 변경분으로 그린 화면이 맞는가

서버는 방이 바뀔 때마다(참여·나가기·합승 종료·30분 자동 종료) `version` 을 정확히 1 올린다(생성 시 0, 생성자가 들어 있는 상태). 방 상세와 `connected` 끝, `member-joined`(`{ member, status, version }`), `member-left`(`{ publicId, status, hostPublicId, version }`), `closed`(`{ partyId, status, version }`)에 실린다. 30초마다 `:ping` 주석이 온다(연결 유지). k6 는 앱과 같은 규칙으로 화면을 갱신한다(`lib/sse.js` 의 `follow`).

| 받은 것 | 처리 |
|---|---|
| `connected` | 새 상세의 `version` 이 화면 이상이면(화면에 아직 없으면 무조건) 통째로 교체, 낮으면 버린다(`sse_stale_event`) |
| `member-*` | 화면 `version`+1 이면 반영하고 화면 `version` 을 올린다. 화면 이하면 버린다(`sse_stale_event`). `connected` 전이거나, 그보다 크거나(번호 빠짐), 정수가 아니면 resync `gap` |
| `closed` | `version` 과 상관없이 종료(직전과 같은 `version` 일 수 있다). 옛 서버의 방 번호 문자열(`"42"`)도 받는다 |

반영은 했지만 화면을 믿을 수 없을 때도 resync 한다 - 들어온·나간 사람을 모름(`unknown-member`), `member-left` 의 `hostPublicId` 가 null 이거나 목록에 없음(`unknown-host`), 목록이 status 와 모순(`drift`). resync 는 스트림을 끊지 않고 상세를 1회 다시 읽어(`GET /matching/rooms/{id} (resync)` - 앱도 하는 호출이지만 이름표로 따로 잡힌다) 그 `version` 이 화면 이상일 때만 통째로 바꾼다.

놓친 이벤트는 다음 변경분에서 번호가 빠진 것으로 드러나 resync 로 메워진다. 뒤에 변경분이 없으면 에러로 끊겨 재연결할 때(D7 은 hold 마다) `connected` 로 메워지고, D2 에서 재연결이 없으면 `missed-event` 로 드러난다. 그래도 화면이 맞는지 **조용한 시점에만** 방 상세를 1회 읽어 맞춰 본다. 호출은 `GET /matching/rooms/{id} (check)` 로 따로 잡혀 앱 호출 수에 안 섞인다.

| 지표 (k6 → Prometheus) | 기록 시점 | 뜻 |
|---|---|---|
| `sse_view_check` → `k6_sse_view_check_total{result}` | D2: 대기 중 `COMPLETED` 를 본 직후(정원이 차 더 바뀌지 않는다) / D7: settle 뒤 | 멤버 publicId 집합 → status → 방장 → `version` 순으로 비교해 처음 어긋난 것 하나. `match` · `members` · `status` · `host` · `version`. 어긋나면 VU 당 5건까지 `console.warn` 에 partyId 와 양쪽 요약(`version` 포함) |
| `sse_resync` → `k6_sse_resync_total{reason}` | D2 SSE 대기·D7 holder 가 상세를 다시 읽을 때 | `gap`(번호 빠짐·`connected` 전 변경분) · `unknown-member` · `unknown-host` · `drift` |
| `sse_resync_result` → `k6_sse_resync_result_total{result}` | resync 상세를 읽은 뒤 | `applied`(교체) · `stale`(상세 `version` 이 화면보다 낮아 버림) · `failed`(200 이 아니거나 JSON 이 아님) |
| `sse_stale_event` → `k6_sse_stale_event_total{name}` | `version` 이 화면 이하라 버린 `member-*`, 화면보다 낮은 `connected` | resync 로 이미 반영된 변경분이 늦게 도착한 것이면 정상 |
| `journey_give_up` → `k6_journey_give_up_total{cause}` | D2·D6 대기 포기 때 | SSE: 상세를 1회 읽어 이미 `COMPLETED` 면 `missed-event`(방은 찼는데 신호를 못 받았다), 아니면 `not-full`. 폴링 앱은 `poll-timeout`, 합승 탭에서 방을 못 찾았으면 `no-room`, 상세 조회 실패는 `unknown` |
| `sse_reconnects` → `k6_sse_reconnects_total` | D2 SSE 가 에러로 끊겨 다시 붙을 때 | 앱처럼 3초 뒤 남은 대기 시간 안에서 재연결. 새 `connected` 는 `version` 이 화면 이상이면 화면을 통째로 바꾼다. 403·404 는 재연결하지 않는다 |
| `sse_propagation_ms` → `k6_sse_propagation_ms_p95` 등 | D7 `propagation`: 측정 방의 `member-joined` 를 받았을 때 | join 요청 직전(t0) → 구독자가 그 사람의 `member-joined` 를 읽은 시각(t1). join 응답 시간이 들어가 보수적이다(아래) |
| `sse_propagation_miss` → `k6_sse_propagation_miss_total{reason}` | D7 `propagation` 이 못 잰 회차 | `connect`(구독·`connected` 실패) · `join`(참여 실패, 409 등 - 지난 실행 잔여면 매번 난다) · `timeout`(`PROBE_TIMEOUT_SEC` 안에 미도착) |

- 방장 승계: `member-left` 의 `hostPublicId` 가 남은 목록에 있으면 방장을 옮기고, null 이거나 목록에 없으면 resync `unknown-host` 로 상세를 다시 읽는다 - 앱과 같다. 예전에는 k6 가 다시 읽지 않아 `host` 어긋남을 기준에서 뺐지만, 이제 k6 도 resync 하므로 `host`·`version` 도 기준에 넣는다
- D7 settle: churn 은 마지막 단계 끝까지(`CHURN_FOR` 로 바꿀 수 있다) 돌고, 멈춘 뒤 `SETTLE`초(기본 10) 지난 시각을 걸친 hold 만 그 뒤 첫 `:ping` 에서 닫고 비교한다. 서버 ping 은 전역 30초 주기라 hold 는 그때만 닫힌다 - 마지막 단계가 그만큼(+약 40초) 늘어난다. churn 중에 끝난 hold 는 도착 중인 변경분 때문에 비교하지 않는다
- 처음에는 **기준 없이 측정만** 한다(스크립트의 `count>=0` 은 태그별로 요약에 보이게 하려는 표시용이고 `summarize.mjs` 는 THRESHOLDS 에서 뺀다 - `sse_resync{reason}`·`sse_resync_result{result}` 도 같다). 몇 번 돌려 0 이 확인되면 아래를 thresholds·알림에 건다

  ```
  'sse_view_check{result:members}': ['count==0'],
  'sse_view_check{result:status}': ['count==0'],
  'sse_view_check{result:host}': ['count==0'],
  'sse_view_check{result:version}': ['count==0'],
  'journey_give_up{cause:missed-event}': ['count==0'],      // D2 USE_SSE=1
  ```
  Grafana: `k6_sse_view_check_total{result=~"members|status|host|version"}`, `k6_journey_give_up_total{cause="missed-event"}`, `k6_sse_reconnects_total`, `k6_sse_resync_total{reason}`
- resync 비율: 경고 `SseResyncRate` 는 `gap` resync 가 `member-*` 이벤트의 5% 를 넘으면 울린다. 참여 직후 자기 참여 신호가 `connected` 보다 먼저 도착하는 경합이 있어 0 이 아닐 수 있다 - 임계값은 기준선을 잰 뒤 조정한다

### SSE 전파 지연 - D7 `propagation`

서버 응답·이벤트에 발생 시각(`joinedAt`)이 없고 **서버 쪽 전파 지연 Timer 도 없다** - k6 로만 잰다. 서버 코드를 바꾸지 않고 한 VU 가 두 시각을 찍어 잰다(시계 차이 없음).

- setup 이 측정 방을 `PROBE_VUS`개(기본 5) 만든다. 정원 3, 방장(watcher)만 들어간 `ACTIVE`. holders 와 같은 구간(램프 다운 전까지) 동안 VU 하나가 방 하나를 맡아 반복한다
- 한 회차: watcher 로 구독 → `connected` 콜백 안에서 t0 → prober 로 join(동기 http) → prober publicId 의 `member-joined` 를 읽으면 t1, `sse_propagation_ms` = t1 - t0 → 구독 종료 → prober leave → 1~2초 쉼
- **값은 보수적(실제보다 크게)이다.** t0 이 join 요청 직전이라 join 응답 시간이 통째로 들어간다. 서버는 커밋 직후(응답 전) Redis 로 발행하지만, 콜백이 http 로 막힌 동안 k6 는 이벤트를 못 읽는다. 하한 ≈ `POST /matching/rooms/{id}/join` 응답 시간
- 미도착 판정은 `PROBE_TIMEOUT_SEC`(기본 10) 뒤 첫 `:ping`(전역 30초 주기)에서 한다 - 판정까지 최대 40초. 그래서 시나리오 `gracefulStop` 은 45초다
- 측정 방은 다른 방이라 holders 의 `sse_view_check` 에 섞이지 않는다. 회차마다 연결을 새로 열므로 `sse_connect_ms`·`sse_events` 에는 같이 잡힌다
- teardown: 측정 방은 `ACTIVE` 라 finish 가 안 된다(`PARTY_NOT_COMPLETED`). 남은 prober 를 내보내고 방장이 나가 `CANCELED` 로 닫는다
- 처음에는 기준 없이 측정만 한다. `PROBE_VUS=0` 이면 끈다(사용자도 `CONNS`명이면 된다)

### 사용자 슬롯
3명이 한 조다 (방장 `users[3g]`, 참여자 `users[3g+1]`, `users[3g+2]`). **D 시나리오끼리는 동시에 돌리지 말 것** — 같은 사용자를 쓴다.

| 시나리오 | 필요한 사용자 |
|---|---|
| D0 | 0..2 |
| D1·D5 | 0..59 (`GROUPS`=20) |
| D2·D6 | 0..`VUS`-1 |
| D3 | 0..89 (`ROOMS`=10 + `ROOMS`x`RACERS`=80) |
| D4 | 0..`CONNS`-1 |
| D1-SSE | 0..59 (`GROUPS`=20, 전부 꽉 찬 방) |
| D7 | 0..`CONNS`-1 (holders·churn) + `CONNS`..`CONNS`+2x`PROBE_VUS`-1 (propagation: 방 p 의 watcher `CONNS+2p`, prober `CONNS+2p+1`) |

### 주의
- **외부 API 에 부하를 걸지 않는다.** 방 생성은 좌표를 고정해 경로 캐시(`routes:*`, TTL 10분)에 적중시키고 방 구분은 `destination` 이름으로만 한다. 캐시가 만료되는 10분마다 네이버 호출이 몇 건 나간다. 장소 검색(카카오)은 시나리오에 없다.
- **seed 사용자에게 FCM 토큰을 등록하지 말 것** — 채팅 알림이 실제 FCM 으로 나간다.
- 테스트가 중간에 죽으면 `ACTIVE` 방에 갇힌 사용자가 남는다(스윕은 `COMPLETED` 만 닫는다). 새 `RUN_ID` 로 다시 seed 하거나 Neon 브랜치를 새로 뜬다.
- **D3 은 동시 요청이 Hikari 풀(10)을 넘는다.** 5xx 나 30초 멈춤이 나오면 커넥션 풀 데드락이다 - 서버 로그에서 `Connection is not available` 을 찾을 것. `-e ROOMS=3 -e RACERS=3` 으로 낮춰 스크립트 자체는 확인할 수 있다.
- D4·D2 의 전달 지연은 보낸 쪽과 받는 쪽이 같은 k6 장비라 시계 오차가 없다. k6 를 여러 대로 나누면 이 지표는 못 쓴다.
- D2 는 반영 지연을 재지 않는다. 서버 응답에서 `joinedAt` 이 빠져 남이 들어온 시각을 k6 가 알 수 없다. 대신 D7 `propagation` 이 같은 VU 안에서 join 과 수신을 찍어 `sse_propagation_ms` 를 잰다(위 SSE 전파 지연). D2 에 넣지 않는 건 부하 모양을 바꾸지 않기 위해서다.
- xk6-sse 는 `:ping` 주석도 event 콜백으로 올린다(name 빈 문자열). 스크립트는 그걸 틱으로 써서 `HOLD_SEC` 가 지나면 닫는다. 서버 `:ping` 이 30초라 실제 유지 시간은 `HOLD_SEC` 보다 최대 30초 길 수 있다.
- SSE 지표의 `url` 태그: 방마다 시계열이 생기지 않게 `sse.open` 의 `tags.url` 을 `name` 과 같은 고정 문자열로 준다. 다만 xk6-sse v0.2.0 소스는 사용자 태그를 적용한 **뒤** `url` 시스템 태그를 실제 URL 로 다시 써서 효과가 없을 수 있다. Prometheus 에서 `k6_sse_event_total` 의 `url` 이 방마다 갈라져 보이면 `--system-tags`(옵션 `systemTags`) 목록에서 `url` 을 빼고 돌린다 - 그러면 `tags.url` 이 남고, http 지표는 `url` 없이 `name` 으로만 갈린다.


## 결과 보관

터미널 끝에 찍히는 요약은 세션이 끝나면 사라진다. `--summary-export=results/<testid>.json` 으로 JSON 을 남기고, 사람이 읽을 형식으로 바꿔 함께 둔다.

```bash
node summarize.mjs results/d2-sse-4000-r7070.json "2026-10-06 21:58 KST, VUS=4000 USE_SSE=1" > results/d2-sse-4000-r7070.txt
```

`results/` 의 `.json` 은 정본(모든 지표·분위수), `.txt` 는 터미널 요약과 같은 형식이다. 파일 이름은 `--tag testid=` 와 같게 둔다 - Grafana 에서 같은 이름으로 찾는다.
Grafana 에는 k6 가 미리 계산한 분위수(p95·p99)만 있고 avg·med 는 없다. 그 값은 여기 파일에서 본다.

## 택시 켜짐 시나리오 (S)
```
k6 run -o experimental-prometheus-rw s1-polling.js        # 20 RPS 5분
k6 run s2-join-race.js                                     # 정합성 - 2 성공 / 8 PARTY_FULL
k6 run -o experimental-prometheus-rw s3-driver-location.js # TAXI_ENABLED=true 서버
k6 run -o experimental-prometheus-rw s5-open-party.js      # loadtest 프로필 서버 (RouteFinder 가짜)
k6 run -o experimental-prometheus-rw s6-breakpoint.js      # 한계 찾기
```
슬롯: 0..19 S1·S6 방장 / 1..10 S2 참여자 (S1 과 동시 실행 금지) / 100..149 S3 기사 / 150..179 S5 방 생성자.

## WAS 1대 vs 2대 비교

dev 서버 A(2a)·C(2c) 앞에 내부 ALB `moyeota-dev-alb` 가 있다. k6 는 VPC 안에서 ALB 의 프라이빗 IP 로 바로 가므로 NAT·IGW 를 거치지 않는다. 비교는 대상 그룹 `moyeota-dev-tg` 에서 C 를 **등록 해제 / 등록**만 바꿔 가며 같은 시나리오를 돌린다.

| 실행 | 대상 그룹 | 보는 것 |
|---|---|---|
| ① 직접 | `BASE_URL=http://172.16.1.30:8080` (ALB 없음) | 기준선 |
| ② ALB + 1대 | A 만 등록 | ALB 오버헤드 (①과 p95 차이) |
| ③ ALB + 2대 | A + C 등록 | 스케일아웃 효과. 서버 CPU 는 instance_name 으로 갈라 보고, RDS CPU 가 다음 병목인지 확인 |

- 세 실행은 **같은 인스턴스 사양·같은 VUS·같은 RUN_ID 규칙**으로 돌린다. 사양을 바꿨으면 ①부터 다시 잰다
- 2대일 때 확인할 것: SSE 가 어느 서버에 붙었든 `member-joined`·`member-left` 가 전파되는지(Redis pub/sub, `sse_events` 의 name 태그별 수), ShedLock 으로 스케줄러가 한 대만 도는지(`job-lock:moyeota:*` 키), `sql/verify.sql` 의 `missing_members` 0
- 등록 해제 뒤 ALB 가 기존 연결(SSE)을 끊는 데 등록 취소 지연(기본 300초)이 걸린다. 실행 사이에 그만큼 기다리거나 대상 그룹 속성에서 줄인다
- ALB 유휴 제한 시간은 60초. SSE 하트비트(30초)가 있어 끊기지 않는다

