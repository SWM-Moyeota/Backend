# Redis Cluster 위에서의 매칭 분산 락

브랜치 `feature/matching-redis-lock-cluster`. 기준은 `feature/matching-redis-lock-sentinel`(PR #124). 기록일 2026-09-20. Sentinel 문서(`sentinel.md`)를 먼저 읽는 것을 전제로 한다.

## Cluster 가 무엇인가

Cluster 는 Redis 의 **샤딩 + 고가용성** 구성이다. 키 공간을 16,384개 슬롯으로 나누고(`CRC16(key) mod 16384`), 여러 마스터가 슬롯 범위를 나눠 가진다. 마스터마다 복제본을 두면 마스터가 죽었을 때 그 샤드의 복제본이 승격한다. Sentinel 과 달리 별도 감시 프로세스가 없다. 노드끼리 클러스터 버스(데이터 포트 + 10000)로 가십을 주고받으며 장애 판정과 승격 투표를 직접 한다.

- **라우팅**: 클라이언트가 `CLUSTER SLOTS` 로 슬롯 맵을 받아 키마다 담당 마스터에 직접 보낸다. 잘못 보내면 `MOVED` 응답으로 정정한다.
- **다중 키 제약**: 한 명령이나 Lua 스크립트가 건드리는 키들은 같은 슬롯에 있어야 한다(`{해시태그}` 로 묶는다). 아니면 `CROSSSLOT`.
- **장애 전환**: 마스터가 `cluster-node-timeout` 동안 응답이 없으면 과반 마스터가 FAIL 로 판정하고, 그 샤드의 복제본이 승격한다. 다른 샤드는 영향이 없다.
- **복제는 비동기**. Sentinel 과 같은 성질이다.

Sentinel 이 "마스터 하나를 안전하게 갈아끼우는" 구성이라면, Cluster 는 "마스터를 여러 개로 쪼개고 각각을 갈아끼우는" 구성이다. 쓰기 처리량과 메모리가 마스터 수만큼 늘고, 장애의 반경은 샤드 하나로 줄어든다. 대신 운영 단위가 6노드 이상이고, 키 설계에 슬롯 제약이 들어온다.

## Redisson 이 Cluster 에 붙는 방식

`matching.lock.redis.mode=cluster`(또는 `auto` 에서 `spring.data.redis.cluster.nodes` 가 있을 때). `useClusterServers()` 에 시드 노드 몇 개를 주면 Redisson 이 슬롯 맵을 받아 각 마스터에 연결 풀을 만들고, `scanInterval`(1초)마다 슬롯 맵을 다시 읽어 승격을 따라간다. Cluster 에는 `database` 선택이 없다(항상 0).

매칭 잠금이 Cluster 에서 그대로 동작하는 이유:

- 잠금은 **단일 키 명령**뿐이다. 획득(Lua `SET`류), 갱신(`PEXPIRE`), 해제(소유권 검사 후 `DEL`) 전부 키 하나만 건드리므로 `CROSSSLOT` 이 없다.
- 사용자 락(`moyeota:matching:member:{id}`)과 방 락(`moyeota:matching:party:{id}`)은 서로 다른 슬롯, 보통 서로 다른 마스터에 있다. 참가는 두 락을 **차례로** 잡지 한 명령으로 잡지 않으므로 같은 슬롯일 필요가 없다. 키에 해시태그를 일부러 넣지 않았다. 넣으면 잠금이 한 마스터로 몰려 샤딩 이점이 사라진다.
- 실험에서 사용자 락 100개와 방 락 100개가 세 마스터에 모두 분산되는 것을 확인했다.

```text
앱 ──슬롯 맵──▶ 마스터 A (0-5460)      ──비동기 복제──▶ 복제본 A'
    ├─ member:7 → 슬롯 → 마스터 B (5461-10922) ──────▶ 복제본 B'
    └─ party:3  → 슬롯 → 마스터 C (10923-16383) ─────▶ 복제본 C'
```

## 실험 환경

`infra/matching-lock/compose.cluster.yaml`: 마스터 3 + 복제본 3, `cluster-node-timeout` 3초. Sentinel 실험과 같은 이유로 노드가 호스트 LAN IP 를 `cluster-announce-ip` 로 알린다(`up.sh cluster` 가 계산). `cluster-init` 컨테이너가 한 번 `redis-cli --cluster create ... --cluster-replicas 1` 을 실행한다.

```sh
infra/matching-lock/up.sh cluster
./gradlew matchingLockTopologyTest -Dmatching.lock.topology=cluster
./gradlew matchingLockTopologyTest -Dmatching.lock.topology=cluster -Dmatching.lock.failover=true                                    # watchdog 2초
./gradlew matchingLockTopologyTest -Dmatching.lock.topology=cluster -Dmatching.lock.failover=true -Dmatching.lock.failover.watchdogMs=30000
docker compose -f infra/matching-lock/compose.cluster.yaml down -v
```

## 검증한 것

1. Cluster 로 붙은 두 클라이언트가 같은 키에서 상호 배제되고 watchdog 이 담당 샤드 마스터에서 TTL 을 갱신한다.
2. 사용자 락·방 락 키 각 100개가 세 마스터에 모두 분산된다.
3. `RedisMatchingAdmission` 을 Cluster 연결로 돌려 같은 사용자 16개 요청의 임계 영역 동시 진입이 1이다.
4. 장애 전환 실험: 잠금 키의 슬롯을 가진 **그 샤드의 마스터**를 찾아 `docker compose stop` 하고, 복제본이 승격할 때까지 기다린 뒤 잠금 상태를 기록한다. 전환 중에 **다른 샤드**의 잠금이 정상인지도 본다. 끝나면 노드를 다시 올리고 클러스터가 안정될 때까지 기다린다.

## 장애 전환 결과

원시 결과는 `docs/matching-lock/matching-lock-cluster-failover-watchdog*.json`.

| 항목 | watchdog 2초 | watchdog 30초 (운영 기본) |
| --- | --- | --- |
| 내린 샤드 마스터 → 승격된 복제본 | 7005 → 7003 | 7005 → 7003 |
| 복제본 승격까지 | 5.4초 | 5.9초 |
| 전환 뒤 새 마스터에 잠금 키 | **없음** | 있음 (TTL 22.6초) |
| 원래 소유자가 살아 있는 동안 다른 클라이언트가 획득 | **예 — 두 소유자** | 아니오 |
| watchdog 1.5배 뒤 키 상태 | 없음 | 27.1초로 갱신됨 |
| 원래 소유자의 unlock | `IllegalMonitorStateException` | 정상 |
| 전환 중 다른 샤드의 잠금 | 정상 | 정상 |
| 전환 뒤 같은 슬롯의 새 잠금 | 정상 | 정상 |

읽는 법:

- 잠금의 생존 조건은 Sentinel 과 **같다**. 복제가 먼저 끝났고 TTL 이 전환 시간보다 길면 살아남고, 아니면 유실돼 두 소유자가 생긴다. Cluster 라고 잠금이 더 안전해지지 않는다. 복제가 비동기인 한 같은 문제다.
- 전환 시간은 Sentinel(약 4초)보다 조금 길었다(약 5~6초). `cluster-node-timeout` 3초 + FAIL 판정 가십 + 투표. 운영 watchdog 30초는 여유가 있다.
- Cluster 가 실제로 더 나은 점은 **장애의 반경**이다. 한 샤드가 전환되는 동안 다른 두 샤드의 잠금은 아무 영향이 없었다. Sentinel 에서는 전환 중 모든 잠금이 멈춘다. 매칭 사용자·방 키가 세 마스터에 분산돼 있으므로 한 마스터 장애가 전체 매칭 입장을 멈추지 않는다.
- 겹침의 최종 방어는 여전히 DB(현재 참여 PK, 방 행 잠금)다. Redlock 을 넣지 않는 이유도 같다.

## Cluster 를 고를 때 따져볼 것

- **필요한 것이 가용성인가 확장인가.** 지금 매칭 잠금의 Redis 부하는 요청당 명령 몇 개 수준이라 마스터 하나로 충분하다. 확장이 필요 없으면 Sentinel 이 운영 단위가 작다(3+3 대 6). Cluster 의 값어치는 채팅 위치·경로 캐시·잠금을 같은 Redis 에 얹어 메모리와 쓰기가 커질 때 나온다.
- **키 설계에 슬롯이 들어온다.** 다중 키 Lua 나 트랜잭션이 있으면 해시태그를 설계해야 한다. 이 브랜치의 잠금은 해당 없음. 채팅 위치 CAS(`feat/redis-cluster` 작업)도 방별 해시 한 키라 해당 없다.
- **클라이언트 비용.** 슬롯 맵 갱신, 마스터마다 연결 풀, `MOVED` 처리. Redisson 이 다 해 주지만 연결 수는 마스터 수에 비례한다.
- 운영은 `rediss://` 와 ACL, 그리고 노드를 서로 다른 장애 도메인에 배치하는 것이 전제다. 한 호스트의 컨테이너 6개는 실험이다.

## 참고

- [Redis Cluster specification](https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/)
- [Scale with Redis Cluster](https://redis.io/docs/latest/operate/oss_and_stack/management/scaling/)
- [Redisson Cluster 설정](https://redisson.pro/docs/configuration/#cluster-mode)
