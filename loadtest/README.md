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

## 1차 배포 시나리오 (D)

프론트 폴링 주기 기준: 지도 목록 4초, 대기 화면 상세 4초, 채팅방 id 10초, 채팅 새 메시지 20초(소켓 정상) / 3초(소켓 끊김).
화면 비중을 지도 40% · 대기 35% · 채팅 25% 로 잡으면 **1인당 0.275 RPS** → 동시 접속 100명 = 27.5 RPS + WebSocket 25연결.

```
export BASE_URL=http://172.16.1.30:8080          # 프라이빗 IP - CloudFront 를 거치지 않는다
export K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write
k6 run d0-smoke.js                                                        # 여정 1회 - 반드시 먼저
k6 run d3-join-consistency.js                                             # 정합성 - 방마다 2명만 성공 + 채팅방 멤버 = 방 멤버
k6 run -o experimental-prometheus-rw d5-breakpoint.js                     # 한계 찾기 (10 → 200 RPS)
k6 run -o experimental-prometheus-rw -e CONCURRENT=100 d1-polling.js      # 읽기 폴링 5분
k6 run -o experimental-prometheus-rw -e VUS=90 d2-journey.js              # 전체 여정(메인) 9분
k6 run -o experimental-prometheus-rw -e CONNS=150 d4-chat-ws.js           # 채팅 WebSocket
k6 run -o experimental-prometheus-rw -e CONNS=150 -e MODE=fallback d4-chat-ws.js   # 소켓이 죽어 전원 3초 폴링으로 떨어진 상황
k6 run -o experimental-prometheus-rw -e VUS=30 d6-soak.js                 # 45분 지속 + 방치된 방 자동 종료
```

| # | 확인하는 것 | 합격선 |
|---|---|---|
| D0 | 여정이 끝까지 도는가 (config → 생성 → 참여 → COMPLETED → 채팅 → 종료) | 체크 100% |
| D1 | 폴링이 목표 RPS 를 버티는가 | 요청별 p95 < 300ms, 실패 < 1% |
| D2 | 실제 비율로 섞였을 때 | p95 < 500ms, 채팅 전달 p95 < 500ms, 409 외 참여 실패 0 |
| D3 | 정원 초과 없음 + 비동기 채팅방 입장·퇴장이 방 멤버와 일치 | 체크 100%, 끝난 뒤 `event_publication` 0행 |
| D4 | 동시 연결 한계·전달 지연 / fallback 모드의 폴링 폭증 | 전달 p95 < 500ms, STOMP 에러 0 |
| D5 | 어디서 꺾이는가 | 기준 없음 - `hikaricp_connections_pending`(DB) vs `system_cpu_usage`(앱) |
| D6 | 메모리·CPU 크레딧, 방치된 방이 30~40분 안에 FINISHED 되고 멤버가 풀려나는가 | 자동 종료 ≤ 42분 |

순서: D0 → D3 → D5 로 천장 → 그 60~70% 로 D1·D2 → D4 → D6.

### 사용자 슬롯
3명이 한 조다 (방장 `users[3g]`, 참여자 `users[3g+1]`, `users[3g+2]`). **D 시나리오끼리는 동시에 돌리지 말 것** — 같은 사용자를 쓴다.

| 시나리오 | 필요한 사용자 |
|---|---|
| D0 | 0..2 |
| D1·D5 | 0..59 (`GROUPS`=20) |
| D2·D6 | 0..`VUS`-1 |
| D3 | 0..89 (`ROOMS`=10 + `ROOMS`x`RACERS`=80) |
| D4 | 0..`CONNS`-1 |

### 주의
- **외부 API 에 부하를 걸지 않는다.** 방 생성은 좌표를 고정해 경로 캐시(`routes:*`, TTL 10분)에 적중시키고 방 구분은 `destination` 이름으로만 한다. 캐시가 만료되는 10분마다 네이버 호출이 몇 건 나간다. 장소 검색(카카오)은 시나리오에 없다.
- **seed 사용자에게 FCM 토큰을 등록하지 말 것** — 채팅 알림이 실제 FCM 으로 나간다.
- 테스트가 중간에 죽으면 `ACTIVE` 방에 갇힌 사용자가 남는다(스윕은 `COMPLETED` 만 닫는다). 새 `RUN_ID` 로 다시 seed 하거나 Neon 브랜치를 새로 뜬다.
- D4·D2 의 전달 지연은 보낸 쪽과 받는 쪽이 같은 k6 장비라 시계 오차가 없다. k6 를 여러 대로 나누면 이 지표는 못 쓴다.

## 택시 켜짐 시나리오 (S)
```
k6 run -o experimental-prometheus-rw s1-polling.js        # 20 RPS 5분
k6 run s2-join-race.js                                     # 정합성 - 2 성공 / 8 PARTY_FULL
k6 run -o experimental-prometheus-rw s3-driver-location.js # TAXI_ENABLED=true 서버
k6 run -o experimental-prometheus-rw s5-open-party.js      # loadtest 프로필 서버 (RouteFinder 가짜)
k6 run -o experimental-prometheus-rw s6-breakpoint.js      # 한계 찾기
```
슬롯: 0..19 S1·S6 방장 / 1..10 S2 참여자 (S1 과 동시 실행 금지) / 100..149 S3 기사 / 150..179 S5 방 생성자.
