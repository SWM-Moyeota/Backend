package team.codingforest.moyeota.matching.infrastructure.topology;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *  실제 Redis Sentinel(마스터 1 + 복제본 2 + Sentinel 3) 위에서 매칭 잠금을 검증한다.
 *  infra/matching-lock/compose.sentinel.yaml 을 띄운 뒤 ./gradlew matchingLockTopologyTest -Dmatching.lock.topology=sentinel
 *  장애 전환 실험은 -Dmatching.lock.failover=true 를 추가한다. 마스터 컨테이너를 실제로 내렸다가 다시 올린다.
 */
@Tag("matching-lock-topology")
@EnabledIfSystemProperty(named = "matching.lock.topology", matches = "sentinel")
class SentinelLockTopologyTest {
    static final String MASTER_NAME = "moyeota";
    static final String[] SENTINELS = {"redis://127.0.0.1:26381", "redis://127.0.0.1:26382", "redis://127.0.0.1:26383"};
    static final Path COMPOSE = Path.of("infra/matching-lock/compose.sentinel.yaml");
    static final Map<String, String> SERVICE_BY_PORT = Map.of("6381", "redis-1", "6382", "redis-2", "6383", "redis-3");

    private static RedissonClient first;
    private static RedissonClient second;

    static RedissonClient client(long watchdogMs) {
        Config config = new Config();
        config.setLockWatchdogTimeout(watchdogMs);
        config.setNettyThreads(2);
        config.setThreads(2);
        config.useSentinelServers().setMasterName(MASTER_NAME).addSentinelAddress(SENTINELS)
                .setCheckSentinelsList(false).setScanInterval(500)
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
    void 센티널로_연결한_두_클라이언트가_상호_배제되고_watchdog이_갱신한다() throws Exception {
        String key = "test:matching:sentinel:" + UUID.randomUUID();
        RLock a = first.getLock(key);
        RLock b = second.getLock(key);

        assertThat(a.tryLock(1, TimeUnit.SECONDS)).isTrue();
        try {
            Thread.sleep(1_500);   // 최초 TTL(watchdog 1초)보다 길게 - 갱신이 안 되면 여기서 만료된다
            assertThat(a.remainTimeToLive()).as("watchdog 이 마스터에서 TTL 을 갱신한다").isPositive();
            assertThat(b.tryLock(0, TimeUnit.SECONDS)).as("다른 클라이언트는 같은 마스터에서 거절된다").isFalse();
        } finally {
            a.unlock();
        }
        assertThat(b.tryLock(1, TimeUnit.SECONDS)).isTrue();
        b.unlock();
    }

    @Test
    void 센티널_구성에서_동일_사용자_입장은_직렬화된다() throws Exception {
        // RedisMatchingAdmission 을 실제 Sentinel 연결로 돌린다. DB 트랜잭션은 가짜로 두고 임계 영역 동시 진입 수만 센다.
        @SuppressWarnings("unchecked") ObjectProvider<RedissonClient> clients = mock(ObjectProvider.class);
        when(clients.getObject()).thenReturn(first);
        MatchingTransactions transactions = mock(MatchingTransactions.class);
        when(transactions.execute(any())).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        RedisMatchingAdmission admission = new RedisMatchingAdmission(transactions, clients);

        long member = 900_000L + ThreadLocalRandomHolder.nextLong();
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

        assertThat(peak.get()).as("같은 사용자의 임계 영역은 동시에 하나만").isEqualTo(1);
        assertThat(done.get()).isEqualTo(16);
    }

    // ───────────────────────── 장애 전환 실험 ─────────────────────────

    @Test
    @EnabledIfSystemProperty(named = "matching.lock.failover", matches = "true")
    void 마스터를_내리면_센티널이_승격하고_잠금이_어떻게_되는지_기록한다() throws Exception {
        String key = "test:matching:sentinel:failover:" + UUID.randomUUID();
        String[] before = masterAddress();
        String stopped = SERVICE_BY_PORT.get(before[1]);
        assertThat(stopped).as("현재 마스터 %s 가 compose 서비스 중 하나여야 한다", String.join(":", before)).isNotNull();

        // watchdog 을 장애 전환 시간(약 5초)보다 짧게/길게 바꿔 가며 잠금이 살아남는지 본다. 기본 2초
        long watchdogMs = Long.getLong("matching.lock.failover.watchdogMs", 2_000L);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("watchdogMs", watchdogMs);
        result.put("masterBefore", String.join(":", before));
        RedissonClient holder = client(watchdogMs);
        RedissonClient other = client(watchdogMs);
        try {
            RLock held = holder.getLock(key);
            held.lock();
            result.put("ttlBeforeMs", held.remainTimeToLive());

            Instant t0 = Instant.now();
            compose("stop", stopped);
            String[] after = waitForNewMaster(before, Duration.ofSeconds(30));
            long failoverMs = Duration.between(t0, Instant.now()).toMillis();
            result.put("stoppedService", stopped);
            result.put("masterAfter", String.join(":", after));
            result.put("failoverMs", failoverMs);

            Thread.sleep(1_500);   // 클라이언트가 새 마스터로 재연결할 시간
            long ttlAfter = held.remainTimeToLive();   // 새 마스터 기준. -2 면 키가 없다(유실)
            result.put("ttlAfterFailoverMs", ttlAfter);
            result.put("holderStillThinksHeld", held.isHeldByCurrentThread());

            boolean otherAcquired = other.getLock(key).tryLock(0, TimeUnit.SECONDS);
            result.put("otherAcquiredWhileHolderAlive", otherAcquired);   // true 면 두 소유자 - 잠금이 장애 전환을 넘지 못했다
            if (otherAcquired) other.getLock(key).unlock();

            // 이전 소유자가 갱신을 이어가는지: watchdog 의 1.5배를 기다린 뒤 키 상태
            Thread.sleep(watchdogMs * 3 / 2 + 500);
            result.put("ttlAfterGraceMs", held.remainTimeToLive());
            boolean otherAcquiredLater = other.getLock(key).tryLock(0, TimeUnit.SECONDS);
            result.put("otherAcquiredAfterGrace", otherAcquiredLater);
            if (otherAcquiredLater) other.getLock(key).unlock();

            try { held.unlock(); result.put("holderUnlock", "ok"); }
            catch (RuntimeException e) { result.put("holderUnlock", e.getClass().getSimpleName()); }

            // 복구 확인: 새 마스터에서 새 잠금이 정상 동작한다
            RLock fresh = other.getLock(key + ":fresh");
            assertThat(fresh.tryLock(1, TimeUnit.SECONDS)).as("장애 전환 뒤 새 잠금은 정상이어야 한다").isTrue();
            fresh.unlock();
            result.put("freshLockAfterFailover", "ok");
        } finally {
            compose("start", stopped);
            holder.shutdown();
            other.shutdown();
            Files.createDirectories(Path.of("build"));
            Files.writeString(Path.of("build/matching-lock-sentinel-failover-watchdog" + watchdogMs + ".json"), toJson(result));
            System.out.println("FAILOVER " + toJson(result));
        }
    }

    // ───────────────────────── 도구 ─────────────────────────

    /** sentinel-1 에 물어본 현재 마스터 [ip, port] */
    static String[] masterAddress() throws IOException, InterruptedException {
        String out = run("docker", "compose", "-f", COMPOSE.toString(), "exec", "-T", "sentinel-1",
                "redis-cli", "-p", "26381", "sentinel", "get-master-addr-by-name", MASTER_NAME);
        String[] lines = out.strip().split("\\R");
        if (lines.length < 2) throw new IllegalStateException("마스터 주소를 읽지 못함: " + out);
        return new String[]{lines[0].strip(), lines[1].strip()};
    }

    static String[] waitForNewMaster(String[] before, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            try {
                String[] now = masterAddress();
                if (!now[1].equals(before[1])) return now;
            } catch (RuntimeException | IOException ignored) {
                // Sentinel 이 아직 판정 중이면 잠시 뒤 다시
            }
            Thread.sleep(200);
        }
        throw new AssertionError("제한 시간 안에 새 마스터로 전환되지 않았다");
    }

    static void compose(String action, String service) throws IOException, InterruptedException {
        run("docker", "compose", "-f", COMPOSE.toString(), action, service);
    }

    static String run(String... command) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        // exec/stop/start 는 컨테이너를 새로 만들지 않지만 compose 가 파일을 해석하며 HOST_IP 를 요구한다 - 자리만 채운다
        builder.environment().putIfAbsent("HOST_IP", "0.0.0.0");
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

    static final class ThreadLocalRandomHolder {
        static long nextLong() { return java.util.concurrent.ThreadLocalRandom.current().nextLong(1, 90_000); }
    }
}
