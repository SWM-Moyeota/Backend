package team.codingforest.moyeota.matching.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.ClusterServersConfig;
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
 *  - cluster  : 시드 노드 목록으로 슬롯 맵을 받아 키마다 담당 마스터로 보낸다. 샤드 마스터가 바뀌면 슬롯 맵을 다시 읽는다
 *  - auto     : spring.data.redis.cluster.nodes 가 있으면 cluster, sentinel.master 가 있으면 sentinel, 아니면 single
 *  잠금 명령(SET NX / Lua 해제 / watchdog 갱신)은 어느 모드든 그 키의 마스터 하나에만 간다. 복제는 비동기라 장애 전환 순간 잠금이 유실될 수 있다
 *  - docs/matching-lock/sentinel.md, cluster.md
 */
@Slf4j
@Configuration
public class MatchingRedisLockConfig {
    static final String MODE_AUTO = "auto";
    static final String MODE_SINGLE = "single";
    static final String MODE_SENTINEL = "sentinel";
    static final String MODE_CLUSTER = "cluster";

    @Bean(destroyMethod = "shutdown")
    @Lazy
    public RedissonClient matchingRedissonClient(
            @Value("${matching.lock.redis.mode:auto}") String mode,
            @Value("${matching.lock.redis.address:redis://localhost:6379}") String address,
            @Value("${matching.lock.redis.username:}") String username,
            @Value("${matching.lock.redis.password:}") String password,
            @Value("${matching.lock.redis.database:0}") int database,
            @Value("${matching.lock.redis.sentinel.master:}") String sentinelMaster,
            @Value("${matching.lock.redis.sentinel.nodes:}") String sentinelNodes,
            @Value("${matching.lock.redis.cluster.nodes:}") String clusterNodes) {
        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        config.setLockWatchdogTimeout(30_000);
        config.setNettyThreads(4);
        config.setThreads(2);

        String resolved = resolveMode(mode, sentinelMaster, clusterNodes);
        switch (resolved) {
            case MODE_CLUSTER -> configureCluster(config, clusterNodes, username, password);
            case MODE_SENTINEL -> configureSentinel(config, sentinelMaster, sentinelNodes, username, password, database);
            case MODE_SINGLE -> configureSingle(config, address, username, password, database);
            default -> throw new IllegalArgumentException("지원하지 않는 matching.lock.redis.mode: " + mode);
        }
        log.info("매칭 잠금 Redis 연결 mode={}", resolved);
        return Redisson.create(config);
    }

    static String resolveMode(String mode, String sentinelMaster, String clusterNodes) {
        if (MODE_AUTO.equals(mode)) {
            if (!clusterNodes.isBlank()) return MODE_CLUSTER;
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
        SentinelServersConfig server = config.useSentinelServers()
                .setMasterName(masterName)
                .addSentinelAddress(addresses(nodes))
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

    private static void configureCluster(Config config, String nodes, String username, String password) {
        if (nodes.isBlank()) {
            throw new IllegalArgumentException("cluster 모드에는 matching.lock.redis.cluster.nodes 가 필요하다");
        }
        // Cluster 는 database 선택이 없다(항상 0). 잠금 키 하나가 슬롯 하나에 매핑되므로 사용자 락과 방 락이 다른 마스터에 있어도 된다 - 키를 넘나드는 명령이 없다
        ClusterServersConfig server = config.useClusterServers()
                .addNodeAddress(addresses(nodes))
                .setScanInterval(1_000)         // 슬롯 맵 재조회 주기 - 샤드 마스터 승격을 1초 안에 따라간다
                .setCheckSlotsCoverage(true)
                .setMasterConnectionMinimumIdleSize(1).setMasterConnectionPoolSize(8)
                .setSlaveConnectionMinimumIdleSize(1).setSlaveConnectionPoolSize(4)
                .setSubscriptionConnectionMinimumIdleSize(1).setSubscriptionConnectionPoolSize(4)
                .setConnectTimeout(1_000).setTimeout(1_000).setRetryAttempts(0);
        if (!username.isBlank()) server.setUsername(username);
        if (!password.isBlank()) server.setPassword(password);
    }

    private static String[] addresses(String nodes) {
        return Arrays.stream(nodes.split(","))
                .map(String::strip).filter(s -> !s.isEmpty())
                .map(s -> s.startsWith("redis://") || s.startsWith("rediss://") ? s : "redis://" + s)
                .toArray(String[]::new);
    }
}
