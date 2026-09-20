package team.codingforest.moyeota.matching.infrastructure;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import team.codingforest.moyeota.matching.domain.MatchingAdmission;
import java.time.Duration;

/** Redisson과 같은 DB, 서비스, 불변식 및 부하 시나리오를 상속하여 비교한다. */
@Import(LettuceMatchingConcurrencyTest.Config.class)
class LettuceMatchingConcurrencyTest extends MatchingConcurrencyTest {
    @org.springframework.beans.factory.annotation.Autowired StringRedisTemplate redis;

    @org.junit.jupiter.api.Test
    void 만료된_소유자의_해제가_새_소유자의_잠금을_삭제하지_않는다() throws Exception {
        String key = "moyeota:matching:experiment:" + java.util.UUID.randomUUID();
        try {
            redis.opsForValue().setIfAbsent(key, "old", Duration.ofMillis(100));
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
            while (Boolean.TRUE.equals(redis.hasKey(key)) && System.nanoTime() < deadline) Thread.sleep(20);
            org.assertj.core.api.Assertions.assertThat(redis.opsForValue().setIfAbsent(key, "new", Duration.ofSeconds(5))).isTrue();
            org.assertj.core.api.Assertions.assertThat(redis.execute(LettuceMatchingAdmission.RELEASE, java.util.List.of(key), "old")).isZero();
            org.assertj.core.api.Assertions.assertThat(redis.opsForValue().get(key)).isEqualTo("new");
            org.assertj.core.api.Assertions.assertThat(redis.execute(LettuceMatchingAdmission.RELEASE, java.util.List.of(key), "new")).isEqualTo(1);
        } finally { redis.delete(key); }
    }

    @TestConfiguration
    static class Config {
        @Bean LettuceConnectionFactory experimentRedisConnectionFactory() {
            var server = new RedisStandaloneConfiguration(
                    System.getenv().getOrDefault("MATCHING_EXPERIMENT_REDIS_HOST", "localhost"),
                    Integer.parseInt(System.getenv().getOrDefault("MATCHING_EXPERIMENT_REDIS_PORT", "6379")));
            return new LettuceConnectionFactory(server,
                    LettuceClientConfiguration.builder().commandTimeout(Duration.ofSeconds(1)).build());
        }
        @Bean StringRedisTemplate experimentRedisTemplate(LettuceConnectionFactory factory) {
            return new StringRedisTemplate(factory);
        }
        @Bean @Primary MatchingAdmission experimentAdmission(MatchingTransactions transactions, StringRedisTemplate redis) {
            return new LettuceMatchingAdmission(transactions, redis);
        }
    }
}
