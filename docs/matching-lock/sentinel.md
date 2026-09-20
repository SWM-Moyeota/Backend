# Redis Sentinel 위에서의 매칭 분산 락

브랜치 `feature/matching-redis-lock-sentinel`. 기준은 `feature/matching-redis-concurrency`(PR #117, Redisson 사용자별 잠금). 기록일 2026-09-20.

같은 토폴로지를 채팅 위치 CAS 관점에서 다룬 `feat/redis-sentinel`(`docs/redis/README.md`)과는 목적이 다르다. 그쪽은 Spring Data Redis(Lettuce) 연결과 단일 키 원자 갱신이고, 여기는 Redisson 분산 락이 장애 전환을 어떻게 넘기는가다.

## Sentinel 이 무엇인가

Sentinel 은 Redis 의 고가용성 구성이다. 데이터 노드는 마스터 1개와 그 복제본 N개이고, 별도의 Sentinel 프로세스 3개 이상이 마스터를 감시한다.

- **감시**: 각 Sentinel 이 마스터에 PING 을 보내고 `down-after-milliseconds` 안에 응답이 없으면 "주관적 다운(SDOWN)"으로 본다.
- **합의**: quorum(여기서는 2) 개 이상의 Sentinel 이 동의하면 "객관적 다운(ODOWN)"이 되고, Sentinel 들이 리더를 뽑아 장애 전환을 시작한다.
- **승격**: 복제본 하나를 골라 `REPLICAOF NO ONE` 으로 마스터로 올리고, 나머지 복제본을 새 마스터에 붙인다.
- **안내**: 클라이언트는 Redis 주소가 아니라 Sentinel 주소 목록과 마스터 이름(`moyeota`)만 안다. 접속할 때 Sentinel 에게 "지금 마스터가 누구냐"고 묻고, 전환이 일어나면 Sentinel 의 Pub/Sub 알림(`+switch-master`)으로 새 주소를 받는다.

쓰기는 항상 마스터 하나가 받는다. 복제는 **비동기**라 마스터가 죽는 순간 아직 복제본에 도착하지 않은 쓰기는 사라진다. 샤딩이 없으므로 쓰기 처리량은 늘지 않는다. 이 두 성질이 분산 락에서 무엇을 뜻하는지가 이 문서의 핵심이다.

## Redisson 이 Sentinel 에 붙는 방식

`MatchingRedisLockConfig` 에 `matching.lock.redis.mode` 를 두었다.

- `single`: 주소 하나 (기존)
- `sentinel`: `useSentinelServers()` 에 마스터 이름과 Sentinel 주소 목록을 준다. Redisson 이 Sentinel 에게 마스터를 물어 붙고, `scanInterval`(1초)마다 그리고 Sentinel 알림으로 마스터 변경을 따라간다.
- `auto`(기본): `spring.data.redis.sentinel.master` 가 설정돼 있으면 sentinel, 아니면 single. 앱의 Redis 토폴로지를 그대로 승계하므로 운영에서 값 하나만 바꾸면 된다. 앞 문서의 한계였던 "Sentinel/Cluster 설정을 자동 승계하지 않는다"를 이 브랜치가 푼다.

잠금에 쓰는 명령(`SET NX PX` 에 해당하는 Lua, 소유권 검사 후 `DEL`, watchdog 의 `PEXPIRE` 갱신)은 전부 마스터로 간다. 복제본에서 잠금을 읽는 일은 없다.

```text
앱 ──(마스터 누구?)──▶ Sentinel ×3 ──감시──▶ 마스터(6381) ──비동기 복제──▶ 복제본(6382, 6383)
 └────────────── 잠금 명령은 마스터로만 ────────────────┘
```

## 실험 환경

`infra/matching-lock/compose.sentinel.yaml`: 마스터 1 + 복제본 2 + Sentinel 3(quorum 2, down-after 2초, failover-timeout 10초). Docker Desktop(macOS)에서는 host 네트워크 포트가 호스트로 열리지 않아, 노드가 자신을 호스트 LAN IP 로 알리고 포트를 공개한다. `infra/matching-lock/up.sh sentinel` 이 `HOST_IP` 를 계산해 띄운다. 호스트 JVM 의 테스트와 컨테이너 안의 Sentinel 이 같은 주소로 노드에 닿는다.

```sh
infra/matching-lock/up.sh sentinel
./gradlew matchingLockTopologyTest -Dmatching.lock.topology=sentinel                                   # 연결·계약·직렬화
./gradlew matchingLockTopologyTest -Dmatching.lock.topology=sentinel -Dmatching.lock.failover=true      # 마스터를 실제로 내린다 (watchdog 2초)
./gradlew matchingLockTopologyTest -Dmatching.lock.topology=sentinel -Dmatching.lock.failover=true -Dmatching.lock.failover.watchdogMs=30000
docker compose -f infra/matching-lock/compose.sentinel.yaml down -v
```

`matchingLockTopologyTest` 는 `matching-lock-topology` 태그만 돌리고 기본 `test` 는 이 태그를 제외한다. CI 에 컨테이너를 요구하지 않는다.

## 검증한 것

1. Sentinel 로 붙은 두 클라이언트가 같은 키에서 상호 배제되고, watchdog 이 마스터에서 TTL 을 갱신한다.
2. `RedisMatchingAdmission` 을 Sentinel 연결로 돌려 같은 사용자 16개 요청의 임계 영역 동시 진입이 1이다.
3. 장애 전환 실험: 클라이언트 A 가 잠금을 쥔 채 현재 마스터 컨테이너를 `docker compose stop` 하고, Sentinel 이 새 마스터를 고를 때까지 기다린 뒤 잠금 상태를 기록한다. 끝나면 노드를 다시 올린다.

## 장애 전환 결과

두 번 돌렸다. watchdog(잠금 TTL 갱신 주기이자 초기 TTL)만 다르다. 원시 결과는 `docs/matching-lock/matching-lock-sentinel-failover-watchdog*.json`.

| 항목 | watchdog 2초 | watchdog 30초 (운영 기본) |
| --- | --- | --- |
| Sentinel 이 새 마스터를 고르기까지 | 4.3초 | 3.7초 |
| 전환 뒤 새 마스터에 잠금 키가 있는가 | **없음** (TTL -2) | 있음 (남은 TTL 24.8초) |
| 원래 소유자 A 가 아직 소유 중이라고 보는가 | 아니오 | 예 |
| A 가 살아 있는 동안 B 가 같은 키를 잡을 수 있는가 | **예 — 두 소유자** | 아니오 |
| watchdog 1.5배 뒤 키 상태 | 없음 | 29.4초로 갱신됨 (A 가 새 마스터에서 계속 갱신) |
| A 의 unlock | `IllegalMonitorStateException` | 정상 |
| 전환 뒤 새 잠금 획득 | 정상 | 정상 |

읽는 법:

- **잠금이 장애 전환을 넘기려면 두 조건이 동시에 필요하다.** ① 잠금 SET 이 마스터가 죽기 전에 복제본까지 도착했을 것(비동기 복제라 보장이 아니라 확률이다) ② TTL 이 전환 시간보다 길 것. 2초 실험은 ②가 깨진 경우다. 전환 4.3초 동안 아무도 갱신하지 못해 복제본에서 키가 만료됐고, 새 마스터에는 키가 없다. A 의 코드는 아직 임계 영역을 실행 중인데 B 가 들어온다.
- 30초 실험은 ①②가 모두 성립한 경우다. 키가 복제본에 있었고 만료되지 않아 새 마스터로 넘어갔다. Redisson 은 Sentinel 알림으로 새 마스터에 재연결해 갱신을 이어갔고 unlock 도 성공했다. 이 실험에서는 잠금을 잡고 몇 초 뒤에 마스터를 내렸으므로 ①이 성립할 시간이 충분했다. 잠금 직후(수 ms 안)에 마스터가 죽는 경우는 재현하지 않았고, 그때는 30초여도 유실된다.
- 따라서 **Sentinel 은 "Redis 가 죽어도 매칭 입장이 계속 되게" 하는 장치이지, "잠금이 절대 겹치지 않게" 하는 장치가 아니다.** 겹침을 막는 마지막 방어선은 앞 문서와 같이 DB 다. `active_match_participation` 의 사용자 PK 와 방 행 잠금이 있어서 두 소유자가 생겨도 진행 중인 참여 1개, 정원 준수라는 불변식은 깨지지 않는다.

이 문제를 Redis 만으로 풀려는 알고리즘이 Redlock(독립 마스터 N 개에서 과반수 획득)이다. Redisson 의 `RedissonRedLock` 이 있지만, 지금 구조에서는 DB 가 최종 판정을 하므로 도입하지 않았다. Redlock 을 넣어도 시계 드리프트와 GC 정지 논쟁은 남는다. 필요한 것은 "잠금이 절대 겹치지 않음"이 아니라 "겹쳐도 데이터가 깨지지 않음"이고, 그건 이미 DB 가 보장한다.

## 운영에 쓸 때 정할 것

- watchdog(30초)은 전환 시간(down-after + 선출 + 승격, 여기서는 4~5초)보다 충분히 길어야 한다. down-after 를 늘리면 전환은 느려지고 잠금은 더 잘 살아남는다. 반대 방향은 없다.
- 전환 중에는 잠금 획득이 `MATCHING_LOCK_UNAVAILABLE`(503)로 실패한다. 우회하지 않는다. 클라이언트는 제한된 횟수만 재시도한다.
- Sentinel 은 노드 3개를 서로 다른 장애 도메인에 둬야 의미가 있다. 한 호스트의 컨테이너 3개는 실험이지 가용성이 아니다.
- 인증·TLS 는 실험 compose 에 없다. 운영은 `rediss://` 와 ACL 을 쓴다.
- Sentinel 은 쓰기 확장을 주지 않는다. 잠금 키가 많아져 마스터 하나가 병목이면 Cluster 를 본다(별도 브랜치 `feature/matching-redis-lock-cluster`).

## 참고

- [Redis Sentinel](https://redis.io/docs/latest/operate/oss_and_stack/management/sentinel/)
- [Distributed Locks with Redis (Redlock 과 단일 노드 한계)](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/)
- [Redisson Sentinel 설정](https://redisson.pro/docs/configuration/#sentinel-mode)
