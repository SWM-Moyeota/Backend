# 부하테스트 시나리오

대상: 모여타 백엔드 (Spring Boot 4, Postgres 16, Redis 7). 도구: k6 + Prometheus + Grafana.
실행 방법과 환경 구성은 [loadtest/README.md](../../loadtest/README.md) 참고.

## 설계 원칙

- **시나리오 단위는 사용자 여정.** 엔드포인트 하나가 아니라 "파티 개설 → 참가 → 배차 → 운행 종료"처럼 실제 사용자가 밟는 흐름 하나가 k6 스크립트 하나다. `SELECT FOR UPDATE` 락 경합이나 아웃박스 이벤트 적체처럼 흐름을 타야 드러나는 병목을 보기 위해서다.
- **부하 모델은 3단계를 모든 시나리오에 공통 적용.** 같은 스크립트를 `PROFILE` 만 바꿔 돌린다.

| 단계 | 목적 | 프로파일 |
|---|---|---|
| 스모크 (`smoke`) | 스크립트·시딩·토큰 발급이 맞는지 확인 | VU 1~3, 1분 |
| 부하 (`load`) | 목표 동시 사용자에서 SLO 충족 여부 판단 | 2분 ramp-up → 목표 VU 10분 유지 → 1분 ramp-down |
| 스트레스 (`stress`) | 무너지는 지점과 먼저 무너지는 구성요소 찾기 | 목표 → 2배 → 4배 계단식, 단계마다 4분 유지 |

- **지표는 공통 지표 + 시나리오 고유 지표.** 공통 지표는 Grafana 대시보드 하나로 모든 회차에서 같이 보고, 고유 지표만 시나리오별로 추가한다.
- **외부 API 는 스텁.** 네이버 경로는 `StubRouteFinder`, FCM 은 `Logging*Notifier` 로 대체한다 (`loadtest` 프로필). 실제 호출은 쿼터를 소진하고 외부 지연이 섞여 앱의 병목을 가린다.

## 목표 부하 (가정치)

서비스 목표치가 정해지지 않아 아래를 가정한다. 확정되면 `TARGET_VUS`, `ONLINE_DRIVERS` 환경변수로 바꾼다.

| 항목 | load 기준값 |
|---|---|
| 동시에 파티를 만들고 있는 승객 그룹 | 100 |
| 동시에 진행 중인 운행 여정 | 50 |
| 온라인 기사 (3초마다 위치 보고) | 200 |
| 파티를 둘러보는 승객 | 20 |

## 공통 지표

| 영역 | 지표 | 출처 |
|---|---|---|
| k6 | 요청 종류별 p95/p99 응답시간, 에러율(`http_req_failed`), 초당 처리량, 활성 VU | k6 → Prometheus remote write |
| JVM | heap 사용량, GC pause, Tomcat busy threads / max threads | `/prometheus` (Micrometer) |
| DB 커넥션 | HikariCP active / pending / 대기 시간 | `/prometheus` |
| 아웃박스 | `moyeota_event_publication_incomplete` (완료 안 된 이벤트 수) | `/prometheus` (EventPublicationMetrics) |
| Postgres | 활성 커넥션, 락 대기, 트랜잭션/초, 캐시 히트율 | postgres_exporter |
| Redis | 명령 처리량, 명령별 지연, 연결 수, 메모리 | redis_exporter |
| 호스트 | CPU, 메모리, 네트워크, 디스크 IO | node_exporter |

공통 SLO: 전체 에러율 < 1%, 전체 p95 < 500ms. 시나리오별로 더 엄격한 값을 아래에 둔다.

---

## 시나리오 1. 승객 매칭 (`matching`)

- **목적**: 파티 개설·참가·조회 경로의 DB 병목을 본다. 특히 같은 파티에 여러 승객이 동시에 참가할 때의 행 락(`findByIdForUpdate`) 경합.
- **앱 설정**: `TAXI_ENABLED=false`. 택시 모듈이 켜져 있으면 정원 충족 즉시 MATCHING 으로 넘어가 승객이 3분간 방에 갇힌다.
- **사전 조건**: 승객 `VU × capacity` 명 시딩. 기사 불필요.
- **흐름** (VU 하나가 승객 `capacity` 명을 대신한다)
  1. 승객 A `POST /matching/rooms` (정원 capacity, 강남역 → 판교)
  2. 나머지 승객이 **동시에** `POST /matching/rooms/{id}/join` → 정원 충족(COMPLETED)
  3. `GET /matching/rooms?swLat…` 지도 범위 조회
  4. `GET /matching/rooms/{id}` 상세
  5. 승객 A `POST /matching/rooms/{id}/finish` 로 방을 닫아 다음 반복에 재사용
- **부하 모델**: `TARGET_VUS=100` (동시 파티 100개). `PARTY_CAPACITY` 2~4 로 락 경합 강도 조절.
- **고유 지표**
  - `matching_cycle_ms`: 개설부터 종료까지 한 사이클
  - `http_req_duration{name=matching_join}` 과 Postgres 락 대기 그래프를 나란히 본다
  - 경로 캐시(`RouteRedisCache`) 히트 여부: 개설 p95 가 Redis GET 시간에 수렴하는지
- **성공 기준**: 개설·참가 p95 < 300ms, 범위 조회 p95 < 200ms, 사이클 p95 < 1.5s, 에러율 < 1%

## 시나리오 2. 정상 운행 종단간 (`ride-e2e`)

- **목적**: 매칭 완료 → 이벤트 발행 → 비동기 배차 → 콜 → 수락 → 운행 종료까지 전체 경로가 목표 동시성에서 끝까지 도는지, 어디서 시간이 새는지 본다.
- **앱 설정**: `TAXI_ENABLED=true` (기본)
- **사전 조건**: 승객 `VU × capacity`, 기사 `VU` 명 (승인 + 콜 수신 켜짐)
- **흐름** (VU 하나 = 승객 capacity 명 + 기사 1명)
  1. 기사 `POST /dispatch/online` (출발지 300m 안)
  2. 파티 개설·참가 → COMPLETED → MATCHING → `MatchingStartedEvent`
  3. 기사가 `GET /dispatch/calls/{id}/status` 를 250ms 간격으로 폴링, `open=true` 까지 걸린 시간 측정
  4. `POST /dispatch/calls/{id}/accept`
  5. 기사 위치 보고 → 승객 `GET /dispatch/rides/{id}` 로 기사 위치 조회
  6. `arrive` → `board` → `complete`
- **부하 모델**: `TARGET_VUS=50`
- **고유 지표**
  - `dispatch_notify_ms`: 정원 충족부터 콜이 열리기까지. 아웃박스 → `@Async` 리스너 → Redis GEOSEARCH 경로의 지연
  - `dispatch_notified`: 15초 안에 콜이 열린 비율. 떨어지면 리스너 스레드 풀이나 이벤트 적체가 원인
  - `moyeota_event_publication_incomplete`: 위 지표와 같은 시간축에 놓고 본다
  - `ride_journey_ms`, `ride_journey_ok`
- **성공 기준**: `dispatch_notify_ms` p95 < 3s, `dispatch_notified` > 99%, 여정 성공률 > 99%, 수락 p95 < 300ms, 기사 위치 조회 p95 < 100ms

## 시나리오 3. 승객 호출과 배차 경쟁 (`dispatch-race`)

- **목적**: 같은 콜을 기사 K 명이 동시에 수락할 때 정확히 한 명만 배정되는지(정합성)와, 경쟁 시 응답시간·실패 처리.
- **앱 설정**: `TAXI_ENABLED=true`
- **사전 조건**: 승객 `VU × capacity`, 기사 `VU × K` 명
- **흐름**
  1. 기사 K 명이 출발지 100m 안에서 온라인
  2. 파티 개설·참가 → 배차 attempt 가 K 명을 한 번에 후보 등록
  3. 콜이 열리면 K 명이 **동시에** `accept`
  4. 204 는 정확히 1건, 나머지는 409 `CALL_CLOSED` 여야 한다
  5. 승자가 `board` → `complete` 로 방을 닫는다
- **부하 모델**: `TARGET_VUS=20`, `RACE_DRIVERS=5`
- **고유 지표**
  - `dispatch_single_winner`: 승자가 정확히 1명인 비율. 99.9% 미만이면 정합성 버그
  - `dispatch_accept_winners`, `dispatch_accept_conflicts`
  - 수락 경로의 Postgres 락 대기와 Redis 후보 집합 연산 지연
- **성공 기준**: `dispatch_single_winner` > 99.9%, 수락 p95 < 300ms (409 포함), 콜 수신 > 99%

## 시나리오 4. 기사 위치 보고 (`driver-location`)

- **목적**: 온라인 기사 전원이 3초마다 보내는 `POST /dispatch/location` (Redis GEOADD) 의 처리량 한계. 배차 탐색(GEOSEARCH)과 같은 키를 쓰므로 이 부하가 커질수록 배차가 느려지는지 mixed 에서 확인한다.
- **사전 조건**: 기사 `ONLINE_DRIVERS` 명. setup 에서 전원 온라인 (강남역 반경 3km 분산)
- **흐름**: constant-arrival-rate 로 초당 `ONLINE_DRIVERS / 3` 건의 위치 보고
- **부하 모델**: `ONLINE_DRIVERS=200` (약 67 rps), stress 는 4배 (약 267 rps)
- **고유 지표**: `http_req_duration{name=dispatch_location}` p95/p99, Redis `geoadd` 명령 지연, Redis CPU, Tomcat busy threads
- **성공 기준**: p95 < 100ms, p99 < 200ms, 에러율 < 0.1%

## 시나리오 5. 정상 서비스 혼합 (`mixed`)

- **목적**: 위 시나리오들이 동시에 돌 때도 각자의 SLO 가 유지되는지. 배경 부하(위치 보고)가 운행 여정 지연에 주는 영향.
- **앱 설정**: `TAXI_ENABLED=true`
- **구성** (k6 scenarios 세 개가 동시에 실행)
  - `location`: 배경 기사 `ONLINE_DRIVERS=100` 명의 위치 보고
  - `ride`: 시나리오 2 여정, `RIDE_VUS=30`
  - `browse`: 승객 20명이 지도 범위 조회 → 무작위 파티 상세, 2~5초 간격
- **고유 지표**: 시나리오 2·4 의 것을 그대로. 단독 실행 대비 `dispatch_notify_ms` 와 `dispatch_location` p95 가 얼마나 나빠지는지 비교
- **성공 기준**: 시나리오 2·4 의 기준을 동시에 만족

## 이후 후보

- **채팅**: STOMP 접속 → 메시지 전송 → 읽음 → 위치 공유. 동시 WebSocket 연결 수와 Redis pub/sub 팬아웃 지연. k6 의 WebSocket 모듈로 작성 가능하나 STOMP 프레임을 직접 만들어야 해서 뒤로 미룬다.
- **HikariCP 풀 크기 비교**: 운영 기본값 10 에서 병목이 확인되면 `DB_POOL_MAX_SIZE` 를 20, 30 으로 바꿔 시나리오 1·2 를 재실행해 비교한다.

## 회차 운영 규칙

1. 회차마다 `TEST_ID` 가 자동으로 붙는다 (`시나리오-프로파일-일시`). Grafana 의 `testid` 변수로 회차를 고른다.
2. 회차 사이에 `loadtest/scripts/reset-db.sh` 로 파티·이벤트·Redis 를 비운다. 이전 파티가 남으면 409 로 에러율이 오염된다.
3. 스모크 → 부하 → 스트레스 순서. 스모크가 깨끗하지 않으면 다음 단계로 가지 않는다.
4. 결과는 `loadtest/results/<TEST_ID>.json` 에 남는다 (git 미추적). 회차별 비교표는 별도 문서로 정리한다.

## 스모크 결과 (2026-09-17, 로컬 macOS · docker Postgres/Redis · loadtest 프로필)

계정 100/60 시딩, VU 3, 1분. 파이프라인(시딩 → k6 → Prometheus remote write → Grafana) 검증 목적이라 성능 수치는 참고만.

| 시나리오 | 결과 | 비고 |
|---|---|---|
| matching (`TAXI_ENABLED=false`, capacity 3) | 통과 | 에러 0/189 |
| ride-e2e | 통과 | 여정 성공 26/26, 콜 지연 p95 271ms (폴링 간격 250ms 포함) |
| dispatch-race (K=5) | **정합성 실패** | `dispatch_single_winner` 0% - 아래 이슈 |
| driver-location | 통과 | 에러 0/80 |
| mixed | 통과 | 에러 0/457 |

### 발견된 이슈: 콜 동시 수락 시 중복 배정

기사 5명이 같은 콜을 동시에 수락하면 5명 전부 204 를 받고 서버 로그에 "콜 수락" 이 5번 찍힌다. `taxi_driver_id` 는 마지막 쓰기가 남고, 나머지 기사는 이후 `board` 에서 403 `NOT_ASSIGNED_DRIVER` 가 된다.

원인: `DispatchService.acceptCall` 트랜잭션 안에서 `hasMemberOnParty` 가 `findById` 로 파티 엔티티를 먼저 적재한 뒤 `assignDriver` 의 `SELECT … FOR UPDATE` 가 실행되는데, Hibernate 는 1차 캐시에 있는 인스턴스를 상태 갱신 없이 돌려주므로 다른 트랜잭션이 커밋한 배정을 보지 못한다. 조건부 UPDATE(`WHERE status='MATCHING' AND taxi_driver_id IS NULL`) 로 바꾸는 것을 권장. 수정 전까지 dispatch-race 는 이 버그를 재현하는 회귀 테스트 역할을 한다.
