package team.codingforest.moyeota.chat.common.order;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RoomOrderedExecutorTest {

    private final RoomOrderedExecutor executor = new RoomOrderedExecutor(new SimpleMeterRegistry());

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void 같은_방의_작업은_넣은_순서대로_실행된다() throws InterruptedException {
        int count = 1_000;
        List<Integer> done = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(count);

        for (int i = 0; i < count; i++) {
            int order = i;
            executor.execute(7L, () -> {
                done.add(order);
                latch.countDown();
            });
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(done).isSorted().hasSize(count);
    }

    @Test
    void 같은_방은_항상_같은_스레드에서_실행된다() throws InterruptedException {
        Set<String> threads = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(100);

        for (int i = 0; i < 100; i++) {
            executor.execute(7L, () -> {
                threads.add(Thread.currentThread().getName());
                latch.countDown();
            });
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(threads).hasSize(1);
    }

    @Test
    void 다른_방은_다른_스레드에서_실행될_수_있다() throws InterruptedException {
        Set<String> threads = ConcurrentHashMap.newKeySet();
        CountDownLatch latch = new CountDownLatch(4);

        for (long room = 0; room < 4; room++) {
            executor.execute(room, () -> {
                threads.add(Thread.currentThread().getName());
                latch.countDown();
            });
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(threads).hasSize(4);
    }
}
