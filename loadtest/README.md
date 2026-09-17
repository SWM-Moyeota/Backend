# 부하 테스트

## 준비
1. 대상 서버: `micrometer-registry-prometheus` 추가, `exposure.include` 에 `prometheus`, prod 는 `management.server.port: 9091` (CloudFront VPC origin 이 8080 을 통째로 전달하므로 관리 포트를 분리해야 인터넷에 안 노출됨). actuator base-path 가 `/` 라 지표는 `http://IP:9091/prometheus`.
   S5 는 `RouteFinder` 를 지연만 흉내 내는 가짜로 바꾼 `loadtest` 프로필로 띄울 것 (네이버 API 호출 금지).
2. k6 서버: `monitoring/prometheus.yml` 의 대상 IP 확인(운영 EC2 172.16.1.30) → `docker compose -f monitoring/docker-compose.yml up -d`
   Grafana(3000) 데이터소스에 `http://prometheus:9090` 추가. 대시보드는 Spring Boot(ID 19004)·k6(ID 18030) 템플릿 import.
3. 사용자 생성 (한 번): `BASE_URL=http://TARGET:8080 USERS=250 node seed.mjs` → `users.json`
   토큰 유효 30분. 지나면 다시 seed.

## 실행
```
export BASE_URL=http://TARGET:8080
export K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write
k6 run -o experimental-prometheus-rw s1-polling.js        # 20 RPS 5분
k6 run s2-join-race.js                                     # 정합성 - 2 성공 / 8 PARTY_FULL
k6 run -o experimental-prometheus-rw s3-driver-location.js # TAXI_ENABLED=true 서버
k6 run -o experimental-prometheus-rw s5-open-party.js      # loadtest 프로필 서버
k6 run -o experimental-prometheus-rw s6-breakpoint.js      # 한계 찾기
```

## 사용자 슬롯 (겹치지 않게)
| 범위 | 용도 |
|---|---|
| 0..19 | S1·S6 방장 |
| 1..10 | S2 참여자 (S1 과 동시 실행 금지) |
| 100..149 | S3 기사 |
| 150..179 | S5 방 생성자 |

## 순서
S6 로 천장을 먼저 잰다 → 그 60~70% 를 S1 의 RPS 로 잡는다 → S3·S5 → S2 는 아무 때나.
