package team.codingforest.moyeota.matching.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SentinelServersConfig;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.Arrays;

/**
 *  매칭 잠금 전용 Redisson 연결. 토폴로지는 mode 로 고른다.
 *  - single   : 주소 하나 (기존 동작)
 *  - sentinel : Sentinel 목록에서 마스터를 찾아 붙는다. 마스터가 바뀌면 Redisson 이 Sentinel 알림으로 따라간다
 *  - auto     : spring.data.redis.sentinel.master 가 있으면 sentinel, 없으면 single. 앱 Redis 토폴로지를 그대로 승계한다
 *  잠금 명령(SET NX / Lua 해제 / watchdog 갱신)은 어느 모드든 마스터 하나에만 간다. 복제는 비동기라 장애 전환 순간 잠금이 유실될 수 있다 - docs/matching-lock-sentinel.md
 */
@Slf4j
@Configuration
public class MatchingRedisLockConfig {
    static final String MODE_AUTO = "auto";
    static final String MODE_SINGLE = "single";
    static final String MODE_SENTINEL = "sentinel";

    @Bean(destroyMethod = "shutdown")
    @Lazy
    public RedissonClient matchingRedissonClient(
            @Value("${matching.lock.redis.mode:auto}") String mode,
            @Value("${matching.lock.redis.address:redis://localhost:6379}") String address,
            @Value("${matching.lock.redis.username:}") String username,
            @Value("${matching.lock.redis.password:}") String password,
            @Value("${matching.lock.redis.database:0}") int database,
            @Value("${matching.lock.redis.sentinel.master:}") String sentinelMaster,
            @Value("${matching.lock.redis.sentinel.nodes:}") String sentinelNodes) {
        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        config.setLockWatchdogTimeout(30_000);
        config.setNettyThreads(4);
        config.setThreads(2);

        String resolved = resolveMode(mode, sentinelMaster);
        switch (resolved) {
            case MODE_SENTINEL -> configureSentinel(config, sentinelMaster, sentinelNodes, username, password, database);
            case MODE_SINGLE -> configureSingle(config, address, username, password, database);
            default -> throw new IllegalArgumentException("지원하지 않는 matching.lock.redis.mode: " + mode);
        }
        log.info("매칭 잠금 Redis 연결 mode={}", resolved);
        return Redisson.create(config);
    }

    static String resolveMode(String mode, String sentinelMaster) {
        if (MODE_AUTO.equals(mode)) {
            return sentinelMaster.isBlank() ? MODE_SINGLE : MODE_SENTINEL;
        }
        return mode;
    }

    private static void configureSingle(Config config, String address, String username, String password, int database) {
        SingleServerConfig server = config.useSingleServer().setAddress(address).setDatabase(database)
                .setConnectionMinimumIdleSize(1).setConnectionPoolSize(8)
                .setSubscriptionConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(4)
                .setConnectTimeout(1_000).setTimeout(1_000).setRetryAttempts(0);
        if (!username.isBlank()) server.setUsername(username);
        if (!password.isBlank()) server.setPassword(password);
    }

    private static void configureSentinel(Config config, String masterName, String nodes, String username, String password, int database) {
        if (masterName.isBlank() || nodes.isBlank()) {
            throw new IllegalArgumentException("sentinel 모드에는 matching.lock.redis.sentinel.master 와 nodes 가 필요하다");
        }
        String[] sentinels = Arrays.stream(nodes.split(","))
                .map(String::strip).filter(s -> !s.isEmpty())
                .map(s -> s.startsWith("redis://") || s.startsWith("rediss://") ? s : "redis://" + s)
                .toArray(String[]::new);
        SentinelServersConfig server = config.useSentinelServers()
                .setMasterName(masterName)
                .addSentinelAddress(sentinels)
                .setDatabase(database)
                .setCheckSentinelsList(false)   // 로컬 실험은 Sentinel 이 3개라도 주소 검증을 느슨하게 둔다
                .setScanInterval(1_000)         // 마스터 변경을 1초 안에 따라간다
                .setMasterConnectionMinimumIdleSize(1).setMasterConnectionPoolSize(8)
                .setSlaveConnectionMinimumIdleSize(1).setSlaveConnectionPoolSize(4)
                .setSubscriptionConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(4)
                .setConnectTimeout(1_000).setTimeout(1_000).setRetryAttempts(0);
        if (!username.isBlank()) server.setUsername(username);
        if (!password.isBlank()) server.setPassword(password);
    }
}
