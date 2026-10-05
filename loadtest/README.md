# 부하 테스트

시나리오는 두 묶음이다.
- **D0~D6 — 1차 배포(택시 꺼짐, `TAXI_ENABLED=false`)**: 매칭 + 채팅만. 지금 쓰는 것.
- **S1~S6 — 택시 켜짐(발표 모드)**: 기사 위치·배차 포함. 택시 기능을 켤 때 다시 쓴다.

## 준비
1. 대상 서버: `micrometer-registry-prometheus` 추가, `exposure.include` 에 `prometheus`, prod 는 `management.server.port: 9091` (CloudFront VPC origin 이 8080 을 통째로 전달하므로 관리 포트를 분리해야 인터넷에 안 노출됨). actuator base-path 가 `/` 라 지표는 `http://IP:9091/prometheus`.
2. k6 서버: `infra/monitoring/prometheus.yml` 의 대상 IP 확인(운영 EC2 172.16.1.30) → 레포 루트에서 `docker compose -f infra/monitoring/docker-compose.yml up -d`
   Grafana(3000) 데이터소스에 `http://prometheus:9090` 추가. 대시보드는 Spring Boot(ID 19004)·k6(ID 18030) 템플릿 import.
3. DB 는 Neon `loadtest` 브랜치로 전환 (서버 env 의 `DATABASE_HOST` 만 교체 → 끝나면 `load-env.sh` 로 원복 후 브랜치 삭제).
   D6 를 돌릴 거면 같은 env 파일에 `JWT_ACCESS_VALIDITY=2h` 도 임시로 넣는다 (기본 30분이면 도중에 토큰 만료).
4. `curl $BASE_URL/api/v1/config` → `{"taxiEnabled":false}` 인지 확인. `true` 면 D 시나리오는 의미가 없다.
5. 사용자 생성: `BASE_URL=http://TARGET:8080 USERS=350 node seed.mjs` → `users.json`
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
| 21 대기 (SSE 후) | `GET /rooms/{id}/events` 연결 1개 유지. `connected`·`changed` 마다 상세 1회, `closed` 면 홈. 서버가 15초마다 `:ping` |
| 채팅 (폴링, SSE 전) | 채팅 탭 `/chat-rooms/me` → 방 열기 3건(첫 페이지·참여자·방 정보) → 소켓 + 읽음(소켓) + 폴링. **21 의 4초 폴링은 채팅 화면 아래에서 계속 돈다** |
| 채팅 (SSE 후) | 위와 같되 방 상세 4초 폴링이 없다. 방 변화는 SSE 로 받고, 놓친 신호에 대비해 `GET /rooms/{id}/status`(상태·인원·지문)만 **30초**마다 확인한다. 지문이 방 상세에서 받은 값과 다르면 그때 상세 1회 |
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
export BASE_URL=http://172.16.1.30:8080          # 프라이빗 IP - CloudFront 를 거치지 않는다
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
./k6 run -o experimental-prometheus-rw -e VUS=90 -e USE_SSE=1 d2-journey.js        # 대기를 SSE 로, 채팅 중에는 방 상태만 30초마다. sse_propagation_ms 가 핵심
./k6 run -o experimental-prometheus-rw -e VUS=90 -e USE_SSE=1 -e LEGACY_DETAIL_POLL=1 d2-journey.js   # 비교용 - 채팅 중 방 상세 4초 폴링(앱이 바뀌기 전)
./k6 run -o experimental-prometheus-rw -e CONNS=300 d7-sse-connections.js          # 연결 100→200→300 + 초당 2건 변화
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
| D2 `USE_SSE=1` | **반영 지연** — 남이 join 한 순간부터 내 화면에 신호가 올 때까지 (폴링은 평균 2초) | `sse_propagation_ms` p95 < 500ms |
| D7 | SSE 동시 연결 한계. `process_open_fds`·힙·`tomcat_threads_busy`(연결이 스레드를 안 잡는지) | 연결 300 에서 propagation p95 유지, `sse_errors` 0 |

순서: D0 → D3 → D5 로 천장 → 그 60~70% 로 D1·D2 → D4 → D6.
SSE 배포 뒤: 폴링 D1 결과를 baseline 으로 두고 → D1-SSE → D2 `USE_SSE=1` → D7. 같은 `CONCURRENT`·`VUS` 로 돌려야 비교가 된다.

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
| D7 | 0..`CONNS`-1 |

### 주의
- **외부 API 에 부하를 걸지 않는다.** 방 생성은 좌표를 고정해 경로 캐시(`routes:*`, TTL 10분)에 적중시키고 방 구분은 `destination` 이름으로만 한다. 캐시가 만료되는 10분마다 네이버 호출이 몇 건 나간다. 장소 검색(카카오)은 시나리오에 없다.
- **seed 사용자에게 FCM 토큰을 등록하지 말 것** — 채팅 알림이 실제 FCM 으로 나간다.
- 테스트가 중간에 죽으면 `ACTIVE` 방에 갇힌 사용자가 남는다(스윕은 `COMPLETED` 만 닫는다). 새 `RUN_ID` 로 다시 seed 하거나 Neon 브랜치를 새로 뜬다.
- **D3 은 동시 요청이 Hikari 풀(10)을 넘는다.** 5xx 나 30초 멈춤이 나오면 커넥션 풀 데드락이다 - 서버 로그에서 `Connection is not available` 을 찾을 것. `-e ROOMS=3 -e RACERS=3` 으로 낮춰 스크립트 자체는 확인할 수 있다.
- D4·D2 의 전달 지연은 보낸 쪽과 받는 쪽이 같은 k6 장비라 시계 오차가 없다. k6 를 여러 대로 나누면 이 지표는 못 쓴다.
- `sse_propagation_ms` 는 서버가 찍은 `joinedAt` 과 k6 의 `Date.now()` 차이다. 둘 다 AWS NTP 라 ms 단위 오차지만 음수가 보이면 시계가 어긋난 것이니 절대값이 아니라 분포로 본다.
- xk6-sse 는 `:ping` 주석도 event 콜백으로 올린다(name 빈 문자열). 스크립트는 그걸 15초 틱으로 쓴다. 서버 heartbeat 주기를 바꾸면 `HOLD_SEC` 해상도가 같이 바뀐다.

## 택시 켜짐 시나리오 (S)
```
k6 run -o experimental-prometheus-rw s1-polling.js        # 20 RPS 5분
k6 run s2-join-race.js                                     # 정합성 - 2 성공 / 8 PARTY_FULL
k6 run -o experimental-prometheus-rw s3-driver-location.js # TAXI_ENABLED=true 서버
k6 run -o experimental-prometheus-rw s5-open-party.js      # loadtest 프로필 서버 (RouteFinder 가짜)
k6 run -o experimental-prometheus-rw s6-breakpoint.js      # 한계 찾기
```
슬롯: 0..19 S1·S6 방장 / 1..10 S2 참여자 (S1 과 동시 실행 금지) / 100..149 S3 기사 / 150..179 S5 방 생성자.
