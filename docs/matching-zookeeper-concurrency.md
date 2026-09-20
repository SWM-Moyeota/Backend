# ZooKeeper 동승 매칭 동시성 대안

이 브랜치는 `develop` 기준의 독립 대안이다. [DB PR #116](https://github.com/SWM-Moyeota/Backend/pull/116), [Redis PR #117](https://github.com/SWM-Moyeota/Backend/pull/117)과 함께 병합하지 않는다. 앞선 PR에서 문서로만 검토한 ZooKeeper를 이번에는 실제 코드와 3노드 시험으로 검증한다.

## 문제와 분석

방 행 잠금만으로는 동일 사용자가 서로 다른 방에 동시에 참가하는 것을 막지 못한다. 사용자별 입장 직렬화, 방 정원 보호, 진행 중인 참여 최대 1개를 각각 보장해야 한다. 기존 조회→각 방 잠금→저장을 재현하면 두 방 참가가 모두 성공하는 시험을 포함했다.

ZooKeeper는 공유 Redis 키의 TTL 대신 세션에 연결된 임시 순차 노드와 앞선 노드의 변경 감시를 사용한다. Apache Curator 5.9.0 `InterProcessMutex`로 이 레시피를 적용했다. 세션 만료로 노드가 사라져도 기존 Java 작업이나 외부 DB 트랜잭션은 자동으로 취소되지 않는다. 따라서 잠금 기술만 교체하고 DB 제약을 제거하지 않는다.

## 구현

1. 경로 조회는 잠금·DB 트랜잭션 밖에서 수행한다.
2. 사용자별 `/moyeota/matching/members/{memberId}` 경로로 `InterProcessMutex`를 획득한다.
3. 사용자 입장 잠금을 얻은 후 DB 트랜잭션을 시작한다. 사용자 진행 중 참여를 검사하고 방 행 잠금으로 정원을 보호한다.
4. `active_match_participation.user_id` PK로 동일 사용자의 활성 참여를 최종 방어한다. 방 저장과 현재 참여 동기화는 같은 트랜잭션에서 수행하고 종료 이력은 유지한다.
5. 커밋 또는 롤백이 끝난 뒤 잠금을 해제한다. 해제 실패가 이미 확정된 결과나 원래 예외를 덮어쓰지 않는다.

`ZooKeeperMatchingClient`는 SUSPENDED/LOST/READ_ONLY를 받으면 로컬 세대 값을 증가시킨다. 신규 연결 확보 시 세대를 기록하고 획득 직후, 트랜잭션 진입 시, `beforeCommit`에서 같은 세대와 연결 상태인지 확인한다. 재연결되어도 이전 세대의 작업은 거절한다. 소유권 불확실·연결 장애는 503, 잠금 획득 대기 초과·인터럽트·DB 잠금 대기 초과는 409로 반환하며 잠금 없는 DB 우회는 하지 않는다.

연결 대기 2초, 잠금 획득 대기 2초, DB 트랜잭션 제한 5초와 PostgreSQL `lock_timeout=2s`를 사용한다. Curator의 개별 네트워크 요청과 재시도까지 포함해 전체 요청 시간이 2초라는 뜻은 아니다. 세션 만료 시간은 서버가 협상한 값에 따라 달라진다.

## 보장 범위와 한계

로컬 세대 값은 **fencing token이 아니다**. 연결 상태 이벤트 전달이 늦거나 `beforeCommit` 검사 직후 단절되면 오래된 작업이 DB 커밋을 시도할 수 있다. 이 경우 동일 사용자의 활성 참여 최대 1개는 DB PK가 방어한다. 새로운 참여가 이미 종료된 뒤 과거 요청이 늦게 실행되는 최신성 문제, HTTP 멱등 응답 저장은 별도 설계 대상이다.

`InterProcessMutex`의 로컬 소유 여부만 보고 세션 유효성을 판단하지 않는다. 세션 만료 후에는 로컬 객체에 소유 흔적이 있어도 서버 노드는 사라질 수 있다. 영속 사용자 부모 경로는 남는다. 운영 정리 작업은 대기자/소유자가 있는 경로를 삭제하지 않아야 하며 이번 PR에 자동 경로 삭제는 포함하지 않았다.

## 장애 실험

`ZooKeeperLockContractTest`는 Curator `TestingCluster(3)`을 사용한다. **독립 포트와 데이터 디렉터리를 가진 실제 ZooKeeper 서버 3개가 하나의 테스트 JVM에서 실행**된다. 단일 서버 모의 객체는 아니지만 여러 호스트·컨테이너의 장애 격리나 네트워크 분할을 검증한 것은 아니다.

| 시험 | 확인하는 결과 |
| --- | --- |
| 독립 클라이언트 두 세션이 같은 경로 획득 | 한 세션만 획득, 해제 후 다른 세션 획득 |
| 연결된 서버 한 개 중단 | 남은 과반으로 실제 잠금 생성·해제 재개 |
| 서버 두 개 중단 | SUSPENDED와 LOST 관측, 기존 세대 및 새 연결 진입 거절 |
| 중단 서버 복구 | 새 세대에서 잠금 획득, 이전 세대는 계속 거절 |
| 클라이언트 세션 만료 이벤트 주입 | 서버의 이전 세션 노드 제거를 기다린 뒤 후속 세션 획득 |
| 이전 소유자의 늦은 해제 | 후속 소유자의 노드 유지, 제3의 획득 시도 차단 |
| DB 저장 후 만료 이벤트 주입 | `beforeCommit`에서 거절되어 방과 현재 참여 함께 롤백 |

만료 이벤트 주입은 `ZooKeeper.getTestable().injectSessionExpiration()`을 사용한다. 실제 네트워크 단절로 서버 세션 타이머가 끝나는 시험과 동일하다고 주장하지 않는다. 과반 상실은 실제 테스트 서버를 중단시켜 별도로 검증한다. 만료 이벤트 자체는 JDBC 실행을 취소하지 않으며 이 특성을 DB 롤백 시험으로 확인한다.

단위 테스트는 연결 장애·획득 실패·인터럽트·획득 후 소유권 불확실·커밋/롤백 이후 해제 실패를 검증한다. 공통 PostgreSQL 시나리오는 동시 생성/참가, 마지막 자리 12명 경쟁, 다른 사용자 병렬 진행, 롤백, 반복 퇴장·재가입, 사용자 락 중첩 시 PK 방어를 포함한다. 채팅방 커밋 후 생성 회귀 시험에도 독립 테스트 앙상블을 연결한다.

## 재현

```sh
docker compose up -d postgres redis
./gradlew test
MATCHING_BENCHMARK=true ./gradlew test --rerun-tasks
```

ZooKeeper 시험 서버는 테스트가 자동 시작·종료한다. PostgreSQL 접속은 `CONCURRENCY_DB_URL`, `CONCURRENCY_DB_USER`, `CONCURRENCY_DB_PASSWORD`로 설정할 수 있다. 기본 localhost:5432/moyeota 및 moyeota 계정을 사용한다. `matching_concurrency_test`와 `matching_migration_test` 전용 스키마를 사용하며 업무 스키마로 바꾸지 않는다. 다른 대안 브랜치의 동시성 시험을 동일 DB에 동시에 실행하지 않는다. 일반 테스트에서 성능 시험 1개는 건너뛰고 위 환경 변수를 켜야 실행한다.

앱 실행에는 접근 가능한 ZooKeeper 앙상블 주소를 `MATCHING_LOCK_ZOOKEEPER_CONNECT`로 설정한다. 기본 `localhost:2181,localhost:2182,localhost:2183`이며 테스트 임의 포트 서버와 별개다. 세션 요청 시간은 `MATCHING_LOCK_ZOOKEEPER_SESSION_TIMEOUT_MS`(기본 15000), 연결 제한은 `MATCHING_LOCK_ZOOKEEPER_CONNECTION_TIMEOUT_MS`(기본 2000)다. 현재 구성은 신뢰된 로컬/사설 실험 환경을 대상으로 하며 인증·ACL·TLS 구성을 제공하지 않는다. 외부 공개나 운영 적용 전 접근 제어와 클러스터 운영 구성이 필요하다.

## 최종 검증 결과와 성능 관측

2026-09-20 `MATCHING_BENCHMARK=true ./gradlew test` 실행 결과 **460개 성공, 실패·오류·건너뜀 0개**다. 초기 전체 실행에서는 채팅 통합 시험이 기본 localhost 앙상블에 연결을 시도해 실패했다. 그 시험에도 자체 3노드 앙상블을 제공한 뒤 전체 회귀 시험을 재실행해 통과했다.

macOS arm64, Java 25, PostgreSQL 16 Docker, Curator 5.9.0 테스트 앙상블 3노드에서 워밍업 20건 뒤 작업자 16개·시나리오당 96건·3회로 측정했다. 각 회차 통계의 중앙값이며 전체 표본을 합친 p95는 아니다. 동일사용자·한방집중은 매회 성공 1건/거절 95건, 분산참가는 성공 96건을 검증한다. 완료 요청/초에는 정상 거절도 포함되며 성공 거래 처리량이 아니다.

| 시나리오 | p50(ms) | p95(ms) | 완료 요청/초 | 관측 최대 DB 연결 |
| --- | ---: | ---: | ---: | ---: |
| 동일사용자 | 262.81 | 285.04 | 62.0 | 1 |
| 한방집중 | 91.08 | 99.16 | 175.4 | 16 |
| 분산참가 | 101.69 | 127.29 | 154.9 | 16 |

지연은 작업자 실행 시작부터 측정해 실행기 큐 대기를 제외한다. DB 연결은 Hikari 사용 중 연결을 5ms 간격으로 관측한 최대치다. 단일 JVM에서 서비스 메서드를 직접 호출하고 경로·외부 의존성을 모의하므로 HTTP·JWT·외부 API·비동기 이벤트 소비 비용은 포함하지 않는다. 원시 회차 기록과 테스트 집계는 `matching-zookeeper-results.json`에 보관했다.

DB와 Redis는 앞선 PR에서 별도 시간에 측정했고 ZooKeeper 서버는 같은 JVM의 테스트 앙상블인 반면 Redis는 Docker 구성이다. 부하 시나리오는 같아도 배치·프로세스·저장장치 경로가 동일하지 않아 수치를 단순 순위로 비교하지 않는다. 이 시험은 정합성과 장애 동작 및 로컬 비용 관측이며 운영 처리량·호스트 장애·네트워크 분할·장시간 부하 성능을 입증하지 않는다.

## 대안 선택

| 방식 | 장점 | 비용과 한계 |
| --- | --- | --- |
| DB 사용자 행 잠금 | 같은 저장소 트랜잭션과 함께 종료, 추가 조정 인프라 없음 | 대기 중 DB 연결 점유 |
| Lettuce 직접 구현 | 토큰·TTL·Lua·재시도 계약을 직접 검증할 수 있음 | 갱신·공정성·오류 처리 유지보수, 앞선 구현은 고정 TTL |
| Redisson | watchdog 및 소유자 검사·알림 대기를 라이브러리에 맡김 | Redis 장애 가정과 DB 최종 방어 필요 |
| ZooKeeper + Curator | 임시 순차 노드·세션·과반 기반 조정, 노드 중단 시험 가능 | 앙상블·세션 관리, 합의 비용, 세션 만료 후 외부 DB 쓰기는 별도 방어 |

현 MVP에는 단일 PostgreSQL의 참가 정합성이 목적이므로 DB 방식을 우선 추천한다. ZooKeeper 시험으로 후보의 보장과 한계를 더 구체적으로 설명할 수 있지만, 구현했다는 이유만으로 운영 도입을 권하지 않는다. 기존 ZooKeeper 인프라와 리더 선출 등 다른 조정 요구가 있을 때 도입 비용을 다시 평가한다. Redlock은 이번에도 구현하지 않았다.

## 마이그레이션과 적용

V7은 `active_match_participation` 생성 후 기존 활성 참여를 backfill한다. 중복 데이터가 있으면 임의 정리하지 않고 실패한다. 적용 전 다음 쿼리로 확인한다.

```sql
SELECT m.user_id, count(*), array_agg(m.match_id ORDER BY m.match_id)
FROM user_match_room m JOIN match_room p ON p.id = m.match_id
WHERE p.status IN ('ACTIVE', 'COMPLETED', 'MATCHING', 'DRIVER_ASSIGNED', 'IN_RIDE')
GROUP BY m.user_id HAVING count(*) > 1;
```

세 대안 PR은 동일 공통 클래스와 V7을 포함한다. 하나만 선택하며 다른 PR이 V7을 먼저 적용한다면 미적용 마이그레이션 번호를 조정한다. 배포 중 이전 노드가 현재 참여 테이블 없이 쓰지 않도록 생성·참가 쓰기를 멈춘 뒤 마이그레이션·새 코드를 적용하고 재개한다. 직접 SQL이나 구버전 저장 경로는 현재 참여를 자동 동기화하지 않는다.

## 공식 근거

- [Curator Shared Reentrant Lock](https://curator.apache.org/docs/recipes-shared-reentrant-lock/)
- [Curator 연결 상태와 오류 처리](https://curator.apache.org/docs/errors/)
- [ZooKeeper 잠금 레시피](https://zookeeper.apache.org/doc/r3.9.3/recipes.html)
- [ZooKeeper 세션과 클라이언트 동작](https://zookeeper.apache.org/doc/r3.9.3/zookeeperProgrammers.html)
