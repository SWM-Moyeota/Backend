package team.codingforest.moyeota.matching.infrastructure;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

@Configuration
public class MatchingRedisLockConfig {
    @Bean(destroyMethod = "shutdown")
    @Lazy
    public RedissonClient matchingRedissonClient(
            @Value("${matching.lock.redis.address:redis://localhost:6379}") String address,
            @Value("${matching.lock.redis.username:}") String username,
            @Value("${matching.lock.redis.password:}") String password,
            @Value("${matching.lock.redis.database:0}") int database) {
        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        config.setLockWatchdogTimeout(30_000);
        config.setNettyThreads(4);
        config.setThreads(2);
        var server = config.useSingleServer().setAddress(address).setDatabase(database)
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(8)
                .setSubscriptionConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(4)
                .setConnectTimeout(1_000).setTimeout(1_000).setRetryAttempts(0);
        if (!username.isBlank()) server.setUsername(username);
        if (!password.isBlank()) server.setPassword(password);
        return Redisson.create(config);
    }
}
