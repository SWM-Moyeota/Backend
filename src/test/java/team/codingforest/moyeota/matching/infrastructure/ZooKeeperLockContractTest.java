package team.codingforest.moyeota.matching.infrastructure;

import org.apache.curator.framework.*;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingCluster;
import org.junit.jupiter.api.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** 3개 서버는 독립 포트/데이터 디렉터리를 쓰지만 같은 JVM에 있다. */
class ZooKeeperLockContractTest {
    TestingCluster cluster;
    CuratorFramework a, b;
    ZooKeeperMatchingClient guard;
    @BeforeEach void start() throws Exception {
        System.setProperty("zookeeper.nio.numWorkerThreads", "4");
        System.setProperty("zookeeper.nio.numSelectorThreads", "1");
        cluster = new TestingCluster(3); cluster.start();
        a = client(); guard = new ZooKeeperMatchingClient(a);
        b = client(); b.start();
        assertThat(a.blockUntilConnected(10, TimeUnit.SECONDS)).isTrue();
        assertThat(b.blockUntilConnected(10, TimeUnit.SECONDS)).isTrue();
    }
    CuratorFramework client() {
        return CuratorFrameworkFactory.builder().connectString(cluster.getConnectString())
                .sessionTimeoutMs(4000).connectionTimeoutMs(2000).retryPolicy(new RetryOneTime(100)).build();
    }
    @AfterEach void stop() throws Exception {
        if (guard != null) guard.close();
        if (b != null) b.close();
        if (cluster != null) cluster.close();
    }
    @Test void 독립_세션은_상호배제하고_해제후_다음_소유자가_획득한다() throws Exception {
        var first = guard.mutex(1L);
        var second = new InterProcessMutex(b, "/moyeota/matching/members/1");
        assertThat(first.acquire(2, TimeUnit.SECONDS)).isTrue();
        try { assertThat(second.acquire(150, TimeUnit.MILLISECONDS)).isFalse(); }
        finally { first.release(); }
        assertThat(second.acquire(2, TimeUnit.SECONDS)).isTrue(); second.release();
    }
    @Test void 세션_만료후_옛_소유자의_해제는_새_소유자의_노드를_삭제하지_않는다() throws Exception {
        var first = guard.mutex(2L);
        var second = new InterProcessMutex(b, "/moyeota/matching/members/2");
        long generation = guard.awaitGeneration();
        assertThat(first.acquire(2, TimeUnit.SECONDS)).isTrue();
        var lost = new CountDownLatch(1);
        a.getConnectionStateListenable().addListener((c, state) -> { if (state == ConnectionState.LOST) lost.countDown(); });
        // 클라이언트 만료 이벤트 주입. 서버의 이전 세션 노드 제거까지는 별도로 기다린다.
        a.getZookeeperClient().getZooKeeper().getTestable().injectSessionExpiration();
        assertThat(lost.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(second.acquire(15, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(a.blockUntilConnected(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> guard.requireGeneration(generation)).isInstanceOf(team.codingforest.moyeota.common.exception.BusinessException.class);
            first.release();
            assertThat(new InterProcessMutex(a, "/moyeota/matching/members/2").acquire(150, TimeUnit.MILLISECONDS)).isFalse();
        } finally { second.release(); }
    }
    @Test void 과반_상실시_소유권을_의심하고_복구후에도_옛_세대를_거절한다() throws Exception {
        long generation = guard.awaitGeneration();
        var lock = guard.mutex(3L);
        assertThat(lock.acquire(2, TimeUnit.SECONDS)).isTrue();
        var suspended = new CountDownLatch(1);
        var lost = new CountDownLatch(1);
        a.getConnectionStateListenable().addListener((c, state) -> {
            if (state == ConnectionState.SUSPENDED) suspended.countDown();
            if (state == ConnectionState.LOST) lost.countDown();
        });
        cluster.getServers().get(0).stop();
        cluster.getServers().get(1).stop();
        try {
            assertThat(suspended.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(lost.await(15, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> guard.requireGeneration(generation)).isInstanceOf(team.codingforest.moyeota.common.exception.BusinessException.class);
            assertThatThrownBy(guard::awaitGeneration).isInstanceOf(team.codingforest.moyeota.common.exception.BusinessException.class);
        } finally {
            cluster.getServers().get(0).restart();
            cluster.getServers().get(1).restart();
        }
        assertThat(a.blockUntilConnected(15, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> guard.requireGeneration(generation)).isInstanceOf(team.codingforest.moyeota.common.exception.BusinessException.class);
        guard.requireGeneration(guard.awaitGeneration());
        lock.release();
        var recovered = new InterProcessMutex(b, "/moyeota/matching/members/3");
        assertThat(recovered.acquire(15, TimeUnit.SECONDS)).isTrue(); recovered.release();
    }
    @Test void 한노드_중단후에도_남은_과반으로_새_잠금을_획득한다() throws Exception {
        var connected = cluster.findConnectionInstance(b.getZookeeperClient().getZooKeeper());
        assertThat(cluster.killServer(connected)).isTrue();
        // blockUntilConnected는 단절 이벤트보다 먼저 반환할 수 있어 실제 서버 요청으로 복구를 검증한다.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        boolean acquired = false;
        while (!acquired && System.nanoTime() < deadline) {
            var lock = new InterProcessMutex(b, "/moyeota/matching/survivor");
            try { acquired = lock.acquire(1, TimeUnit.SECONDS); if (acquired) lock.release(); }
            catch (org.apache.zookeeper.KeeperException e) { Thread.sleep(100); }
        }
        assertThat(acquired).isTrue();
    }
}
