# Elasticsearch 채팅 메시지 검색

## 제공 기능

현재 참여 중인 채팅방의 텍스트 메시지를 검색한다. 기존 `GET /api/v1/chat-rooms/{roomId}/messages/search`는 DB 검색으로 유지하며, 확장 API는 Elasticsearch를 사용한다.

```http
GET /api/v1/chat-rooms/1/messages/search/advanced?keyword=출구&senderId=7&from=2026-09-01T00:00:00Z&until=2026-10-01T00:00:00Z&size=30
Authorization: Bearer <accessToken>
```

- `keyword`: 앞뒤 공백 제거 후 2~100자.
- `senderId`: 선택, 발신자의 내부 사용자 ID. 인증 사용자 ID는 JWT에서만 가져온다.
- `from`, `until`: 선택, ISO-8601 시각. `[from, until)` 범위로 필터링한다.
- `cursor`: 선택, 이전 응답의 `nextCursor`. 메시지 ID 내림차순으로 정렬한다.
- `size`: 1~100, 기본 30.
- 응답: `{ "messages": [{ "message": { 기존 ChatMessageResult 필드 }, "highlight": "<mark>출구</mark> 앞입니다" }], "nextCursor": 123, "hasNext": true }`.
- `highlight`는 Elasticsearch의 HTML encoder를 적용한 `<mark>` 강조 조각이다. 클라이언트에서도 허용 태그를 제한해 표시한다.
- 삭제·권한 검증으로 후보가 제외되면 요청 크기보다 적거나 빈 페이지가 나올 수 있다. `hasNext=true`라면 `nextCursor`로 계속 조회한다. 커서는 반환된 메시지가 아닌 마지막으로 검사한 후보를 기준으로 진행한다.
- 검색 전과 ES 응답 후에 참여 권한을 검사한다. 기존 정책과 동일하게 현재 참여자는 방의 과거 메시지를 검색할 수 있고, 탈퇴한 사용자는 검색할 수 없다. 방 전체 통합 검색이나 입장 시각별 열람 정책은 추가하지 않았다.
- ES 장애·기능 비활성·부분 샤드 실패·검색 시간초과는 `503 CHAT_SEARCH_UNAVAILABLE`. 잘못된 필터는 400이다. 장애를 정상적인 빈 검색 결과로 숨기지 않는다.

## 분석기와 검색 방식

`src/main/resources/elasticsearch/chat-messages-v1.json`에 인덱스 정의가 있다. 본문을 standard 분석 필드와 custom bigram(`ngram`, min=max=2) 하위 필드에 색인하고 `multi_match(type=phrase)`로 조회한다. 예를 들어 `강남역삼번출구`에서 `역삼번출구`를 찾는다. bigram의 위치 정보를 활용해 글자 순서를 유지하며, 긴 검색어도 2글자 토큰의 연속으로 조회한다. 소문자 정규화를 적용한다.

여기서 Multi Match는 제목·발신자 이름을 추가한 것이 아니라 **동일 본문의 두 분석 필드**에 적용한다. 방·작성자·기간은 정확한 필터다. 형태소 분석, 오타 교정, 동의어, 이름 기반 발신자 검색은 구현 범위에 없다. 두 분석 방식의 OR 조건이므로 기존 SQL LIKE와 모든 입력에서 같은 검색 의미를 보장하지 않는다.

검색 결과 순서는 커서와 일관되게 메시지 ID 내림차순이며 관련도순이 아니다. 본문 하위 필드의 일치 위치도 Highlight에 사용한다.

## 저장과 동기화

1. `chat.search.enabled=true`인 서버가 `ChatMessageJpa.save`에서 메시지와 `chat_search_outbox` 스냅샷을 같은 트랜잭션으로 저장한다. 메시지 롤백 시 이벤트도 롤백한다.
2. 전용 스케줄러가 기본 1초 간격, 50건 단위로 전송한다. 다른 매칭 스케줄러와 실행 스레드를 분리한다. HTTP 호출 중 DB 트랜잭션을 유지하지 않는다.
3. 메시지 ID가 ES 문서 ID, outbox ID가 `external_gte` 버전이다. 스냅샷은 불변이므로 동일 이벤트 재시도도 동일한 내용을 기록한다. 더 오래된 버전의 409 응답은 이미 최신 상태가 반영된 것으로 처리한다.
4. 성공하면 해당 outbox 행을 지운다. 실패하면 대기 기록을 유지하고 2초부터 최대 256초까지 지수 백오프한다. 장애를 기록할 때 메시지 본문·검색어·외부 오류 원문을 로그에 남기지 않는다.
5. 삭제는 빈 본문의 `deleted=true` 문서로 덮어쓴다. 삭제 표식을 남기므로 뒤늦은 생성 이벤트가 본문을 복원하지 못한다. 위치 메시지도 검색 대상에서 제외한다.
6. 검색 시 DB 원본을 ID로 일괄 조회하고 삭제 상태, 방, 작성자, 기간, 본문 일치를 다시 확인한다. 색인 반영 전의 삭제 본문이나 변경 전 강조 조각을 반환하지 않는다. 이는 응답 전 DB 검증 시점 기준이며 응답 전송 이후의 삭제까지 소급하는 보장은 아니다.

검색은 최종적 일관성이다. 기본 refresh 1초와 폴링·재시도·밀린 작업에 따른 지연이 있다. 메시지 수정 API는 현재 없지만 저장 경로로 변경한 본문은 새 스냅샷으로 반영된다. 원본 DB에 직접 SQL을 실행하는 변경은 자동 감지하지 않는다.

검색 기능을 끄면 새 이벤트도 기록하지 않는다. 기능을 다시 켠 후에는 기존 데이터 재색인이 필요하다. 기능을 켠 인스턴스와 끈 인스턴스가 섞여 메시지를 저장하는 배포 구간에도 재색인으로 누락을 보정해야 한다.

## 실행과 설정

```sh
# 별도 테스트 서비스: ES 19200, PostgreSQL 15432, Redis 16379
# 기존 개발 서비스와 다른 Compose 프로젝트/포트 사용
docker compose -p moyeota-search-check -f docker-compose.search.yml up -d --wait

# 실제 DB 및 ES 통합 테스트 (테스트 전용 schema와 무작위 ES 인덱스 사용)
./gradlew searchIntegrationTest

# 기존 전체 테스트를 별도 DB/Redis에서 실행
SEARCH_TEST_JDBC_URL=jdbc:postgresql://localhost:15432/search_test \
SPRING_DATA_REDIS_PORT=16379 ./gradlew test

# 합성 100만 건 벤치마크
SEARCH_BENCH_ROWS=1000000 ./gradlew searchBenchmark
```

기본 앱 기동은 ES에 연결하지 않는다. 앱에서 검색을 활성화하려면 `search` 프로필을 추가한다. PostgreSQL을 사용하는 앱은 Flyway 마이그레이션을 먼저 적용한다.

| 설정 | 기본값 / 의미 |
|---|---|
| `chat.search.enabled` | false, 검색과 실시간 색인 이벤트 기록·전송 활성화 |
| `chat.search.url` | 기본 http://localhost:9200, search 프로필은 http://localhost:19200 |
| `chat.search.index` | chat-messages-v1 |
| `chat.search.api-key` | 빈 문자열, 운영 API Key의 인코딩된 값 |
| `chat.search.connect-timeout` | 2초 |
| `chat.search.read-timeout` | 3초 |
| `chat.search.batch-size` | 50, 허용 1~500 |
| `chat.search.poll-delay` | 1000ms |
| `chat.search.initial-delay` | 5000ms |
| `chat.search.backfill` | 기본 미설정, true면 시작 시 기존 메시지를 200건씩 재색인 예약 |

`search` 프로필은 `ELASTICSEARCH_URL`, `ELASTICSEARCH_CHAT_INDEX`, `ELASTICSEARCH_API_KEY` 환경변수를 지원한다. API Key나 실제 메시지 데이터는 저장소에 넣지 않는다. 제공 Compose는 로컬 검증 전용이며 보안을 비활성화하고 loopback에만 포트를 연다. 운영에서는 TLS/인증과 인덱스 접근 권한을 설정한다. 예제 버전은 재현성을 위해 8.19.0으로 고정했으며 운영 버전 선정·보안 업데이트는 별도로 관리한다.

## 기존 데이터 및 복구

`SearchBackfill`은 메시지 행에 비관적 잠금을 잡은 상태에서 현재 스냅샷을 outbox에 넣는다. 삭제 저장과 순서를 맞춰 오래된 본문이 더 높은 이벤트 버전을 받는 것을 방지한다.

- 최초 활성화 시 `--chat.search.backfill=true`로 한 번 실행한다. 처리 중에는 검색 결과가 부분적으로 채워지므로 완료와 대기열 소진을 확인한 뒤 프론트에서 확장 검색을 노출한다.
- 기능을 끈 기간, 인덱스 유실, DB 직접 수정 후에도 재색인한다. 중단 후 재실행은 처음부터 반복하지만 새 이벤트 버전으로 수렴한다.
- 인덱스 정의를 변경할 때는 같은 이름의 기존 매핑을 자동 수정하지 않는다. 현재 구현은 무중단 인덱스 전환을 제공하지 않으므로 쓰기/검색 노출을 통제하는 유지보수 절차에서 새 인덱스 이름으로 모든 인스턴스를 맞추고 재색인한다.
- 자동 인덱스 생성에는 해당 인덱스의 생성·매핑 권한이 필요하다. 이미 있으면 기존 인덱스를 사용한다.
- DB 복원으로 outbox 시퀀스가 과거로 돌아가면 기존 ES 외부 버전과 충돌할 수 있다. 이 경우 새 인덱스에 전체 재색인한다.
- 반복 실패 이벤트는 자동 폐기하지 않는다. 대기열 증가와 오래된 작업을 모니터링하고 원인을 해결한다.

```sql
SELECT count(*) AS pending,
       min(queued_at) AS oldest_pending,
       max(attempts) AS max_attempts
FROM chat_search_outbox;

SELECT id, message_id, attempts, queued_at, next_attempt_at
FROM chat_search_outbox
WHERE attempts > 0
ORDER BY queued_at
LIMIT 100;
```

삭제 표식은 과거 이벤트 재전송을 막는 상태다. 보존 정책을 마련하기 전에는 임의로 지우지 않는다. 보존 기간에 따라 hot/cold와 Rollover를 도입하면 문서 ID가 어느 인덱스에 있는지 추적하고 과거 인덱스에도 삭제를 반영해야 한다. 이번 구현은 단일 버전 인덱스이므로 해당 정책을 도입하지 않았다.

마이그레이션은 `V9__chat_search_outbox.sql`이다. 다른 미병합 기능의 V7/V8과 배포 순서를 맞춰야 하며, 이 브랜치를 먼저 배포한다면 아직 적용되지 않은 마이그레이션들의 번호를 병합 전에 조정한다. 이미 적용한 마이그레이션은 변경하지 않는다.

## CI 등록

`chat-search-test.yml`은 GitHub Actions 등록용 예제다. 현재는 문서 폴더에 있으므로 자동 실행되지 않는다. workflow 수정 권한이 있는 계정으로 `.github/workflows/chat-search-test.yml`에 등록하면 PR마다 실제 PostgreSQL/Elasticsearch 통합 테스트를 실행한다. 기존 빌드 CI는 기본 단위·회귀 테스트를 계속 실행한다.

## 검증과 성능 해석

- 단위·HTTP 계층: 익명 차단, JWT 주체 사용, 검색 전/후 권한 검사, 삭제·다른 방·변경 전 본문 제거, 커서 진행, 필터 검증, 재시도 및 부분 실패 처리.
- H2: 저장/삭제 이벤트, 트랜잭션 롤백, 기존 메시지 재색인.
- 실제 PostgreSQL + Elasticsearch: Flyway와 Hibernate validate, 한국어 부분 일치·강조·HTML 이스케이프, 방/작성자/기간/커서, 이벤트 중복·역순, 장애 후 복구, 롤백, 초기 색인.
- [100만 건 측정 결과](benchmark-1000000.md): 흔한 검색어의 첫 페이지는 DB가 더 빨랐고, 드문 검색어와 결과가 없는 검색은 ES가 더 빨랐다. 결과 ID가 동일한지도 반복 측정마다 검증했다.

이 측정은 합성 데이터·단일 클라이언트의 저장소 호출 비교이며 전체 서비스 API의 100ms 보장이나 동시 처리량 실적이 아니다. 개인 기기 부하와 캐시 상태의 영향을 받으며 production 용량 산정에 직접 쓰지 않는다. 향후 실제 데이터 분포, pg_trgm 최적화, 동시 사용자 부하, 색인 지연과 실패율, 검색 성공률을 추가로 평가한다.

참고: [Ngram tokenizer](https://www.elastic.co/docs/reference/text-analysis/analysis-ngram-tokenizer), [Highlight](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/highlighting), [외부 버전](https://www.elastic.co/guide/en/elasticsearch/reference/8.19/docs-index_.html#index-versioning).
