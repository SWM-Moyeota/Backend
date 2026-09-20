package team.codingforest.moyeota.matching.infrastructure;

import org.junit.jupiter.api.*;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

/** 서로 다른 클라이언트 ID를 가진 두 Redisson 연결로 소유권과 갱신을 검증한다. */
class RedissonLockContractTest {
    private static RedissonClient first;
    private static RedissonClient second;
    private static RedissonClient client() {
        Config config = new Config();
        config.setLockWatchdogTimeout(900);
        config.setNettyThreads(2); config.setThreads(2);
        config.useSingleServer().setAddress(System.getenv().getOrDefault("MATCHING_LOCK_REDIS_ADDRESS", "redis://localhost:6379"))
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(2)
                .setSubscriptionConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(2);
        return Redisson.create(config);
    }
    @BeforeAll static void start() { first = client(); second = client(); }
    @AfterAll static void stop() { if (first != null) first.shutdown(); if (second != null) second.shutdown(); }

    @Test
    void leaseTime을_생략하면_watchdog가_유효시간을_연장한다() throws Exception {
        String key = "test:matching:watchdog:" + UUID.randomUUID();
        var a = first.getLock(key); var b = second.getLock(key);
        assertThat(a.tryLock(1, TimeUnit.SECONDS)).isTrue();
        try {
            Thread.sleep(1500); // 최초 TTL 900ms보다 길게 유지한다.
            assertThat(a.remainTimeToLive()).isPositive();
            assertThat(b.tryLock(0, TimeUnit.SECONDS)).isFalse();
        } finally { a.unlock(); }
        assertThat(b.tryLock(1, TimeUnit.SECONDS)).isTrue();
        b.unlock();
    }

    @Test
    void 만료된_이전_소유자는_새_소유자의_락을_삭제할_수_없다() throws Exception {
        String key = "test:matching:lease:" + UUID.randomUUID();
        var a = first.getLock(key); var b = second.getLock(key);
        assertThat(a.tryLock(0, 200, TimeUnit.MILLISECONDS)).isTrue();
        Thread.sleep(400);
        assertThat(b.tryLock(1, TimeUnit.SECONDS)).isTrue();
        try {
            assertThatThrownBy(a::unlock).isInstanceOf(IllegalMonitorStateException.class);
            assertThat(b.isHeldByCurrentThread()).isTrue();
        } finally { b.unlock(); }
    }
}
