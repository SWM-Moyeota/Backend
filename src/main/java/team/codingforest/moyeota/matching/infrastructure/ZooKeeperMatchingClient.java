package team.codingforest.moyeota.matching.infrastructure;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.state.ConnectionState;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** 연결이 복구되어도 과거 작업의 소유권을 복구된 것으로 취급하지 않는다. */
public class ZooKeeperMatchingClient implements AutoCloseable {
    private final CuratorFramework client;
    private final AtomicLong generation = new AtomicLong();

    public ZooKeeperMatchingClient(CuratorFramework client) {
        this.client = client;
        client.getConnectionStateListenable().addListener((ignored, state) -> {
            if (state == ConnectionState.SUSPENDED || state == ConnectionState.LOST || state == ConnectionState.READ_ONLY) {
                generation.incrementAndGet();
            }
        });
        client.start();
    }

    long awaitGeneration() throws InterruptedException {
        if (!client.blockUntilConnected(2, TimeUnit.SECONDS)) unavailable();
        long current = generation.get();
        requireGeneration(current);
        return current;
    }

    void requireGeneration(long expected) {
        if (generation.get() != expected || !client.getZookeeperClient().isConnected()) unavailable();
    }

    InterProcessMutex mutex(Long memberId) {
        return new InterProcessMutex(client, "/moyeota/matching/members/" + memberId);
    }

    private static void unavailable() {
        throw new BusinessException(MatchingErrorCode.MATCHING_LOCK_UNAVAILABLE);
    }

    @Override public void close() { client.close(); }
}
