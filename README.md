# Backend
## 테스트

| 폴더 | 태스크 | 내용 |
|---|---|---|
| `src/unitTest` | `./gradlew test` | 단위·슬라이스 테스트. 외부 서비스 없이 어디서나 돈다 |
| `src/integrationTest` | `./gradlew integrationTest` | `@SpringBootTest` 등 Redis·Postgres 가 필요한 테스트. 로컬에 `localhost:6379`·`5432` 가 없으면 건너뛴다(SKIPPED) |

`./gradlew build`(= `check`)는 둘 다 돈다. 로컬에서 통합 테스트까지 보려면 `docker compose up -d` 뒤에 실행한다.

## 모니터링 (Grafana)

- 주소: https://grafana.moyeota.p-e.kr (ALB 호스트 규칙 → k6 서버 3000). 서버는 비공개, 보안 그룹이 ALB 에서 오는 3000 만 허용한다
- 기동: k6 서버 `/root/Backend` 에서 `git pull` 뒤 `infra/monitoring/up.sh`. 관리자 비밀번호는 파라미터 스토어 `/moyeota/monitoring/GRAFANA_PASSWORD`
- 외부 공유는 **Viewer 권한 계정**을 만들어 준다. 관리자 계정은 공유하지 않는다. Prometheus(9090)는 인증이 없어 열지 않는다

## 환경과 배포

| 환경 | 브랜치 | CodeDeploy 그룹 | 서버 태그 | 파라미터 | DB |
|---|---|---|---|---|---|
| dev | `develop` 머지 | `moyeota-dev` | `Env=dev` | `/moyeota/dev/*` | `moyeota-dev-db` |
| prod | `main` 머지(릴리스 PR) | `moyeota-prod` | `Env=prod` | `/moyeota/prod/*` | `moyeota-prod-db` |

서버는 자기 EC2 태그 `Env` 를 메타데이터(IMDSv2)로 읽어 어느 파라미터를 쓸지 정한다(`infra/ec2/load-env.sh`). 인스턴스 설정에서 **메타데이터의 태그 허용**이 켜져 있어야 하고, 태그가 없으면 기동하지 않는다. 부하테스트는 dev 만 때린다.
