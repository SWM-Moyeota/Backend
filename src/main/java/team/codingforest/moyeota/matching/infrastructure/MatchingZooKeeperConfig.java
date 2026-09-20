package team.codingforest.moyeota.matching.infrastructure;

import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
public class MatchingZooKeeperConfig {
    @Bean(destroyMethod = "close")
    @Lazy
    ZooKeeperMatchingClient matchingZooKeeperClient(
            @Value("${matching.lock.zookeeper.connect-string:localhost:2181,localhost:2182,localhost:2183}") String connect,
            @Value("${matching.lock.zookeeper.session-timeout-ms:15000}") int sessionTimeout,
            @Value("${matching.lock.zookeeper.connection-timeout-ms:2000}") int connectionTimeout) {
        var client = CuratorFrameworkFactory.builder().connectString(connect)
                .sessionTimeoutMs(sessionTimeout).connectionTimeoutMs(connectionTimeout)
                .retryPolicy(new RetryOneTime(100)).build();
        return new ZooKeeperMatchingClient(client);
    }
}
