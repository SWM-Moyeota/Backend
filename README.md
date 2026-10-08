# Backend
## 테스트

| 폴더 | 태스크 | 내용 |
|---|---|---|
| `src/unitTest` | `./gradlew test` | 단위·슬라이스 테스트. 외부 서비스 없이 어디서나 돈다 |
| `src/integrationTest` | `./gradlew integrationTest` | `@SpringBootTest` 등 Redis·Postgres 가 필요한 테스트. 로컬에 `localhost:6379`·`5432` 가 없으면 건너뛴다(SKIPPED) |

`./gradlew build`(= `check`)는 둘 다 돈다. 로컬에서 통합 테스트까지 보려면 `docker compose up -d` 뒤에 실행한다.
