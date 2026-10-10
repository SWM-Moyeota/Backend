package team.codingforest.moyeota._config;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import team.codingforest.moyeota.common.event.EventResubmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  실제 Redis 로 잠금이 동작하는지 본다. 다른 서버가 잠근 동안에는 @SchedulerLock 메서드의 본문이 실행되지 않아야 하고,
 *  풀리면 실행돼야 한다. "다른 서버"는 LockProvider 로 직접 잠가서 흉내 낸다.
 */
@SpringBootTest
@Import(SchedulerLockIntegrationTest.TestJobs.class)
class SchedulerLockIntegrationTest {
    private static final String NAME = "test-job";

    @Autowired LockProvider lockProvider;
    @Autowired TestJob job;
    @Autowired EventResubmitter resubmitter;
    @Autowired StringRedisTemplate redis;

    @AfterEach
    void clean() {
        redis.delete("job-lock:moyeota:" + NAME);
    }

    @Test
    void 다른_서버가_잠근_동안에는_실행되지_않고_풀리면_실행된다() {
        SimpleLock held = lockProvider.lock(new LockConfiguration(Instant.now(), NAME, Duration.ofSeconds(30), Duration.ZERO)).orElseThrow();

        job.run();
        assertThat(job.runs()).as("잠긴 동안").isZero();

        held.unlock();
        job.run();
        assertThat(job.runs()).as("풀린 뒤").isEqualTo(1);
    }

    @Test
    void 같은_이름의_잠금은_한_번에_하나만_잡힌다() {
        SimpleLock first = lockProvider.lock(new LockConfiguration(Instant.now(), NAME, Duration.ofSeconds(30), Duration.ZERO)).orElseThrow();

        Optional<SimpleLock> second = lockProvider.lock(new LockConfiguration(Instant.now(), NAME, Duration.ofSeconds(30), Duration.ZERO));

        assertThat(second).isEmpty();
        first.unlock();
    }

    @Test
    void 잠금은_Redis_키로_남고_상한이_만료로_걸린다() {
        lockProvider.lock(new LockConfiguration(Instant.now(), NAME, Duration.ofSeconds(30), Duration.ZERO)).orElseThrow();

        assertThat(redis.hasKey("job-lock:moyeota:" + NAME)).isTrue();
        assertThat(redis.getExpire("job-lock:moyeota:" + NAME)).isBetween(1L, 30L);   // 서버가 죽어도 이 시간 뒤엔 풀린다
    }

    @Test
    void 실제_스케줄러_빈은_잠금_프록시로_감싸져_있다() {
        // 프록시가 아니면 어노테이션이 있어도 잠금이 걸리지 않는다
        assertThat(AopUtils.isAopProxy(resubmitter)).isTrue();
    }

    static class TestJob {
        private final AtomicInteger runs = new AtomicInteger();

        @SchedulerLock(name = NAME, lockAtMostFor = "PT10S")
        public void run() {
            runs.incrementAndGet();
        }

        // 필드를 직접 읽으면 안 된다 - 주입되는 빈은 CGLIB 프록시(하위 클래스)라 프록시 자신의 필드는 비어 있다(NPE). 메서드는 원본으로 전달된다
        public int runs() {
            return runs.get();
        }
    }

    @TestConfiguration
    static class TestJobs {
        @Bean
        TestJob testJob() {
            return new TestJob();
        }
    }
}
