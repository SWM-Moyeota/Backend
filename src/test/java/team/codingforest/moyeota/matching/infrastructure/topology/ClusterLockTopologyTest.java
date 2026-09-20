package team.codingforest.moyeota.matching.infrastructure.topology;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.ObjectProvider;
import team.codingforest.moyeota.matching.infrastructure.MatchingTransactions;
import team.codingforest.moyeota.matching.infrastructure.RedisMatchingAdmission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *  실제 Redis Cluster(마스터 3 + 복제본 3) 위에서 매칭 잠금을 검증한다.
 *  infra/matching-lock/up.sh cluster 로 띄운 뒤 ./gradlew matchingLockTopologyTest -Dmatching.lock.topology=cluster
 *  장애 전환 실험은 -Dmatching.lock.failover=true. 잠금 키의 슬롯을 가진 샤드 마스터 컨테이너를 실제로 내렸다가 다시 올린다.
 */
@Tag("matching-lock-topology")
@EnabledIfSystemProperty(named = "matching.lock.topology", matches = "cluster")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)   // 노드를 내렸다 올리는 실험은 맨 마지막 - 재합류 직후의 슬롯 맵 흔들림이 다른 테스트를 깨지 않게
class ClusterLockTopologyTest {
    static final String[] SEEDS = {"redis://127.0.0.1:7001", "redis://127.0.0.1:7002", "redis://127.0.0.1:7003"};
    static final Path COMPOSE = Path.of("infra/matching-lock/compose.cluster.yaml");
    static final Map<String, String> SERVICE_BY_PORT = Map.of("7001", "redis-1", "7002", "redis-2", "7003", "redis-3",
            "7004", "redis-4", "7005", "redis-5", "7006", "redis-6");

    private static RedissonClient first;
    private static RedissonClient second;

    static RedissonClient client(long watchdogMs) {
        Config config = new Config();
        config.setLockWatchdogTimeout(watchdogMs);
        config.setNettyThreads(2);
        config.setThreads(2);
        config.useClusterServers().addNodeAddress(SEEDS).setScanInterval(500).setCheckSlotsCoverage(true)
                .setMasterConnectionMinimumIdleSize(1).setMasterConnectionPoolSize(4)
                .setSlaveConnectionMinimumIdleSize(1).setSlaveConnectionPoolSize(2)
                .setSubscriptionConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(2)
                .setConnectTimeout(1_000).setTimeout(1_000).setRetryAttempts(0);
        return Redisson.create(config);
    }

    @BeforeAll static void start() { first = client(1_000); second = client(1_000); }
    @AfterAll static void stop() { if (first != null) first.shutdown(); if (second != null) second.shutdown(); }

    // ───────────────────────── 연결과 계약 ─────────────────────────

    @Test
    @Order(1)
    void 클러스터로_연결한_두_클라이언트가_상호_배제되고_watchdog이_갱신한다() throws Exception {
        String key = "test:matching:cluster:" + UUID.randomUUID();
        RLock a = first.getLock(key);
        RLock b = second.getLock(key);

        assertThat(a.tryLock(1, TimeUnit.SECONDS)).isTrue();
        try {
            Thread.sleep(1_500);
            assertThat(a.remainTimeToLive()).as("watchdog 이 담당 샤드 마스터에서 TTL 을 갱신한다").isPositive();
            assertThat(b.tryLock(0, TimeUnit.SECONDS)).isFalse();
        } finally {
            a.unlock();
        }
        assertThat(b.tryLock(1, TimeUnit.SECONDS)).isTrue();
        b.unlock();
    }

    @Test
    @Order(2)
    void 사용자_락과_방_락_키는_세_마스터에_고루_분산된다() throws Exception {
        // 키에 {해시태그} 를 쓰지 않으므로 사용자 키와 방 키가 각기 다른 슬롯 → 다른 마스터로 간다. 잠금은 단일 키 명령뿐이라 CROSSSLOT 이 없다.
        Map<Integer, String> masterBySlotStart = masters();   // 슬롯 시작 → 마스터 포트
        Set<String> memberMasters = new HashSet<>(), partyMasters = new HashSet<>();
        for (long id = 1; id <= 100; id++) {
            memberMasters.add(ownerOf(slot(RedisMatchingAdmissionKeys.member(id)), masterBySlotStart));
            partyMasters.add(ownerOf(slot(RedisMatchingAdmissionKeys.party(id)), masterBySlotStart));
        }
        assertThat(masterBySlotStart.values()).hasSize(3);
        assertThat(memberMasters).as("사용자 락 100개가 한 마스터에 몰리면 샤딩 이점이 없다").hasSize(3);
        assertThat(partyMasters).hasSize(3);
    }

    @Test
    @Order(3)
    void 클러스터_구성에서_동일_사용자_입장은_직렬화된다() throws Exception {
        @SuppressWarnings("unchecked") ObjectProvider<RedissonClient> clients = mock(ObjectProvider.class);
        when(clients.getObject()).thenReturn(first);
        MatchingTransactions transactions = mock(MatchingTransactions.class);
        when(transactions.execute(any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        RedisMatchingAdmission admission = new RedisMatchingAdmission(transactions, clients);

        long member = 900_000L + ThreadLocalRandom.current().nextLong(1, 90_000);
        AtomicInteger inside = new AtomicInteger(), peak = new AtomicInteger(), done = new AtomicInteger();
        List<Supplier<?>> work = new ArrayList<>();
        for (int i = 0; i < 16; i++) work.add(() -> admission.execute(member, () -> {
            int now = inside.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try { Thread.sleep(20); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            inside.decrementAndGet();
            return done.incrementAndGet();
        }));
        try (var pool = Executors.newFixedThreadPool(16)) {
            var start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (var item : work) futures.add(pool.submit(() -> { start.await(); return item.get(); }));
            start.countDown();
            for (var future : futures) future.get(30, TimeUnit.SECONDS);
        }
        assertThat(peak.get()).isEqualTo(1);
        assertThat(done.get()).isEqualTo(16);
    }

    // ───────────────────────── 장애 전환 실험 ─────────────────────────

    @Test
    @Order(9)
    @EnabledIfSystemProperty(named = "matching.lock.failover", matches = "true")
    void 잠금_키를_가진_샤드_마스터를_내리면_복제본이_승격하고_잠금이_어떻게_되는지_기록한다() throws Exception {
        String key = "test:matching:cluster:failover:" + UUID.randomUUID();
        int slot = slot(key);
        String ownerBefore = ownerOf(slot, masters());
        String stopped = SERVICE_BY_PORT.get(ownerBefore);
        assertThat(stopped).isNotNull();

        long watchdogMs = Long.getLong("matching.lock.failover.watchdogMs", 2_000L);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("watchdogMs", watchdogMs);
        result.put("slot", slot);
        result.put("ownerBefore", ownerBefore);
        RedissonClient holder = client(watchdogMs);
        RedissonClient other = client(watchdogMs);
        try {
            RLock held = holder.getLock(key);
            held.lock();
            result.put("ttlBeforeMs", held.remainTimeToLive());

            Instant t0 = Instant.now();
            stoppedService = stopped;
            compose("stop", stopped);
            String ownerAfter = waitForNewOwner(slot, ownerBefore, Duration.ofSeconds(30));
            result.put("stoppedService", stopped);
            result.put("ownerAfter", ownerAfter);
            result.put("failoverMs", Duration.between(t0, Instant.now()).toMillis());

            Thread.sleep(1_500);
            result.put("ttlAfterFailoverMs", held.remainTimeToLive());
            result.put("holderStillThinksHeld", held.isHeldByCurrentThread());

            boolean otherAcquired = other.getLock(key).tryLock(0, TimeUnit.SECONDS);
            result.put("otherAcquiredWhileHolderAlive", otherAcquired);
            if (otherAcquired) other.getLock(key).unlock();

            Thread.sleep(watchdogMs * 3 / 2 + 500);
            result.put("ttlAfterGraceMs", held.remainTimeToLive());
            boolean otherAcquiredLater = other.getLock(key).tryLock(0, TimeUnit.SECONDS);
            result.put("otherAcquiredAfterGrace", otherAcquiredLater);
            if (otherAcquiredLater) other.getLock(key).unlock();

            try { held.unlock(); result.put("holderUnlock", "ok"); }
            catch (RuntimeException e) { result.put("holderUnlock", e.getClass().getSimpleName()); }

            // 다른 샤드의 잠금은 전환 내내 영향이 없어야 한다 - 샤딩의 장애 격리
            String otherShardKey = keyOnDifferentShard(ownerBefore);
            RLock unaffected = other.getLock(otherShardKey);
            assertThat(unaffected.tryLock(1, TimeUnit.SECONDS)).as("다른 샤드의 잠금은 정상").isTrue();
            unaffected.unlock();
            result.put("otherShardLockDuringFailover", "ok");

            RLock fresh = other.getLock(key + ":fresh");
            assertThat(fresh.tryLock(1, TimeUnit.SECONDS)).as("전환 뒤 같은 슬롯의 새 잠금은 정상").isTrue();
            fresh.unlock();
            result.put("freshLockAfterFailover", "ok");
        } finally {
            compose("start", stopped);
            stoppedService = "";
            waitForClusterStable(Duration.ofSeconds(30));   // 재합류한 노드가 복제본으로 붙고 클라이언트 슬롯 맵이 갱신될 때까지 - 뒤 테스트를 흔들지 않게
            holder.shutdown();
            other.shutdown();
            Files.createDirectories(Path.of("build"));
            Files.writeString(Path.of("build/matching-lock-cluster-failover-watchdog" + watchdogMs + ".json"), toJson(result));
            System.out.println("FAILOVER " + toJson(result));
        }
    }

    // ───────────────────────── 도구 ─────────────────────────

    static int slot(String key) {
        return first.getKeys().getSlot(key);
    }

    /** 실험 중 내려간 노드. 클러스터 조회는 이 노드를 피해 보낸다 - 내린 노드에 물어보면 영원히 답이 없다 */
    static volatile String stoppedService = "";

    /** 살아 있는 아무 노드에 CLUSTER 하위 명령을 보낸다 */
    static String clusterCommand(String subcommand) throws IOException, InterruptedException {
        IOException last = null;
        for (var entry : SERVICE_BY_PORT.entrySet()) {
            if (entry.getValue().equals(stoppedService)) continue;
            try {
                return run("docker", "compose", "-f", COMPOSE.toString(), "exec", "-T", entry.getValue(),
                        "redis-cli", "-p", entry.getKey(), "cluster", subcommand);
            } catch (IllegalStateException e) {
                last = new IOException(e.getMessage());
            }
        }
        throw last == null ? new IOException("살아 있는 노드가 없다") : last;
    }

    /** CLUSTER NODES 에서 마스터별 슬롯 시작 → 포트. 담당 범위는 연속이라 시작 슬롯으로 찾을 수 있다 */
    static Map<Integer, String> masters() throws IOException, InterruptedException {
        String out = clusterCommand("nodes");
        Map<Integer, String> result = new LinkedHashMap<>();
        for (String line : out.strip().split("\\R")) {
            String[] f = line.split(" ");
            if (f.length < 9 || !f[2].contains("master")) continue;
            String port = f[1].substring(f[1].indexOf(':') + 1, f[1].indexOf('@'));
            for (int i = 8; i < f.length; i++) {
                if (f[i].startsWith("[")) continue;   // 이동 중 슬롯 표기
                result.put(Integer.parseInt(f[i].split("-")[0]), port);
            }
        }
        return result;
    }

    static String ownerOf(int slot, Map<Integer, String> masterBySlotStart) {
        int best = -1;
        for (int start : masterBySlotStart.keySet()) if (start <= slot && start > best) best = start;
        return masterBySlotStart.get(best);
    }

    static String keyOnDifferentShard(String port) throws IOException, InterruptedException {
        Map<Integer, String> masters = masters();
        for (int i = 0; i < 1000; i++) {
            String candidate = "test:matching:cluster:other:" + i;
            if (!port.equals(ownerOf(slot(candidate), masters))) return candidate;
        }
        throw new IllegalStateException("다른 샤드 키를 찾지 못함");
    }

    static String waitForNewOwner(int slot, String before, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try {
                String now = ownerOf(slot, masters());
                if (now != null && !now.equals(before)) return now;
            } catch (RuntimeException | IOException ignored) {
                // 노드 목록이 흔들리는 중
            }
            Thread.sleep(200);
        }
        throw new AssertionError("제한 시간 안에 슬롯 " + slot + " 의 새 마스터가 정해지지 않았다");
    }

    /** 6노드 전부 연결되고 fail 표시가 없으며 cluster_state:ok 가 될 때까지 기다린 뒤, 클라이언트 재조회 주기(500ms)의 여유를 둔다 */
    static void waitForClusterStable(Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try {
                String nodes = clusterCommand("nodes");
                String info = clusterCommand("info");
                long connected = nodes.strip().lines().filter(l -> l.contains(" connected")).count();
                if (connected == 6 && !nodes.contains("fail") && info.contains("cluster_state:ok")) {
                    Thread.sleep(2_000);
                    return;
                }
            } catch (RuntimeException | IOException ignored) {
                // 아직 흔들리는 중
            }
            Thread.sleep(500);
        }
        throw new AssertionError("클러스터가 제한 시간 안에 안정되지 않았다");
    }

    static void compose(String action, String service) throws IOException, InterruptedException {
        run("docker", "compose", "-f", COMPOSE.toString(), action, service);
    }

    static String run(String... command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        builder.environment().putIfAbsent("HOST_IP", "0.0.0.0");   // exec/stop/start 는 재생성이 없어 자리만 채우면 된다
        Process process = builder.start();
        String out = new String(process.getInputStream().readAllBytes());
        if (!process.waitFor(40, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("명령 실패 " + String.join(" ", command) + "\n" + out);
        }
        return out;
    }

    static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> sb.append(sb.length() > 1 ? ", " : "").append('"').append(k).append("\": ")
                .append(v instanceof Number || v instanceof Boolean ? v : "\"" + v + "\""));
        return sb.append('}').toString();
    }

    /** 운영 어댑터와 같은 키 규칙 */
    static final class RedisMatchingAdmissionKeys {
        static String member(long id) { return "moyeota:matching:member:" + id; }
        static String party(long id) { return "moyeota:matching:party:" + id; }
    }
}
