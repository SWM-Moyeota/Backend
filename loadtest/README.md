# 부하테스트 실행 가이드

시나리오 정의와 지표는 [docs/loadtest/scenarios.md](../docs/loadtest/scenarios.md).

```
loadtest/
├── docker-compose.yml        Prometheus + Grafana + node/redis/postgres exporter
├── prometheus/prometheus.yml 스크레이프 대상 (앱은 host.docker.internal:8080)
├── grafana/                  데이터소스·대시보드 자동 프로비저닝 (generate-dashboard.py 로 커스텀 대시보드 생성)
├── k6/
│   ├── seed.js               승객·기사 계정 시딩
│   ├── lib/                  설정, 로그인, API 래퍼, 여정
│   └── scenarios/            matching, ride-e2e, dispatch-race, driver-location, mixed
└── scripts/
    ├── run.sh                k6 실행 + Prometheus remote write + 결과 저장
    └── reset-db.sh           회차 사이 파티·이벤트·Redis 초기화
```

## 1. 로컬에서 한 번 돌려보기

```bash
# 1) Postgres, Redis
docker compose up -d

# 2) 모니터링 스택
docker compose -f loadtest/docker-compose.yml up -d
#    Grafana http://localhost:3000 (admin/admin)  Prometheus http://localhost:9090

# 3) 앱을 loadtest 프로필로
./gradlew bootRun --args='--spring.profiles.active=loadtest'
#    매칭 단독 시나리오만 돌릴 때는 TAXI_ENABLED=false ./gradlew bootRun --args='--spring.profiles.active=loadtest'

# 4) 계정 시딩 (한 번만. 다시 실행해도 409 로 건너뜀)
loadtest/scripts/run.sh seed

# 5) 스모크
loadtest/scripts/run.sh ride-e2e smoke

# 6) 부하 / 스트레스
loadtest/scripts/reset-db.sh
loadtest/scripts/run.sh ride-e2e load
loadtest/scripts/reset-db.sh
loadtest/scripts/run.sh ride-e2e stress
```

`k6` 는 `brew install k6`.

## 2. 실제 서버에 붙이기

운영 EC2 에 직접 걸지 않는다. 운영과 같은 인스턴스 타입으로 부하테스트용 EC2 를 하나 더 두고 아래처럼 구성한다.

| 역할 | 위치 | 비고 |
|---|---|---|
| 앱 (`loadtest` 프로필) | 부하테스트 EC2 | 같은 jar. `SPRING_PROFILES_ACTIVE=loadtest`, `DATABASE_URL`, `JWT_SECRET` 환경변수 |
| Postgres, Redis | 부하테스트 EC2 (docker) 또는 Neon 브랜치 | Neon 을 쓰면 네트워크 왕복과 Neon 커넥션 제한이 먼저 병목이 된다 |
| node_exporter | 부하테스트 EC2 | 앱 호스트 CPU/메모리 |
| Prometheus, Grafana, redis/postgres exporter | 모니터링용 인스턴스 (부하 발생기와 같아도 됨) | `loadtest/docker-compose.yml` 을 그대로 쓰되 `prometheus.yml` 의 타깃을 EC2 사설 IP 로 |
| k6 | 노트북 또는 별도 EC2 (c 계열) | 앱과 같은 머신에서 돌리면 CPU 를 나눠 써 결과가 왜곡된다 |

보안 그룹: 앱 EC2 의 8080(앱), 9100(node_exporter) 은 모니터링/부하 발생기 IP 에서만 허용.

```bash
# 부하 발생기에서
export BASE_URL=http://<앱 EC2 사설 IP>:8080
export PROM_URL=http://<모니터링 IP>:9090
loadtest/scripts/run.sh seed
loadtest/scripts/run.sh mixed load
```

DB 초기화는 `PG="psql postgresql://..." REDIS="redis-cli -h ..." loadtest/scripts/reset-db.sh`.

## 3. 환경변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | 대상 앱 |
| `PROFILE` | `smoke` | `smoke` / `load` / `stress` (run.sh 두 번째 인자) |
| `TARGET_VUS` | 시나리오별 | load 단계 목표 VU. stress 는 2배, 4배 |
| `PARTY_CAPACITY` | 2 | 파티 정원 (2~4). 클수록 참가 락 경합 증가 |
| `RACE_DRIVERS` | 5 | dispatch-race 에서 콜을 경쟁하는 기사 수 |
| `ONLINE_DRIVERS` | 200 (mixed 100) | 위치를 보고하는 기사 수 |
| `PASSENGERS`, `DRIVERS` | 1000, 600 | 시딩 계정 수. seed.js 와 시나리오가 같은 값을 봐야 한다 |
| `DISPATCH_TIMEOUT_MS` | 15000 | 콜이 열리길 기다리는 최대 시간 |
| `TEST_ID` | 자동 | Grafana 회차 태그 |
| `DB_POOL_MAX_SIZE` (앱) | 10 | HikariCP 최대 커넥션. 운영 기본값과 동일 |
| `TAXI_ENABLED` (앱) | true | matching 시나리오는 false |

## 4. Grafana 대시보드

`부하테스트` 폴더에 자동 등록된다.

- **Moyeota 부하테스트**: 시나리오 고유 지표 + 공통 지표를 한 화면에. `testid` 로 회차 선택
- JVM (Micrometer), Node Exporter, Redis, PostgreSQL, k6 Prometheus: grafana.com 공개 대시보드 (4701, 1860, 763, 9628, 19665)

## 5. 자주 나는 문제

- **setup 에서 로그인 실패**: `seed.js` 를 먼저 실행했는지, `PASSENGERS`/`DRIVERS` 가 시딩 때와 같은지 확인.
- **matching 이 시작하자마자 중단**: 앱이 `TAXI_ENABLED=true` 로 떠 있다. false 로 재기동.
- **`ALREADY_JOINED_OTHER_PARTY` 409 가 많다**: 이전 회차 파티가 남아 있다. `reset-db.sh`.
- **`dispatch_notified` 가 낮다**: 리스너 스레드 풀 또는 아웃박스 적체. Grafana 의 `event_publication incomplete` 패널 확인.
- **Prometheus 에 k6 지표가 없다**: `--web.enable-remote-write-receiver` 가 켜져 있는지, `PROM_URL` 이 맞는지.
