package team.codingforest.moyeota.socket.order;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatSeqOrdererTest {

    private static final Long ROOM_A = 10L;
    private static final Long ROOM_B = 20L;

    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final List<String> sent = new ArrayList<>();
    private ChatSeqOrderer orderer;

    @BeforeEach
    void setUp() {
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(mock(ScheduledFuture.class));
        // 대기 상한 0 - 테스트에서 대기 만료 작업을 직접 돌려 시간을 흉내 냄
        orderer = new ChatSeqOrderer(
                scheduler, new ChatOrderProperties(Duration.ZERO, Duration.ofMinutes(10)), new ChatOrderMetrics(registry));
    }

    @Test
    void 순번대로_오면_기다리지_않고_바로_보낸다() {
        offer(ROOM_A, 1);
        offer(ROOM_A, 2);
        offer(ROOM_A, 3);

        assertThat(sent).containsExactly("10:1", "10:2", "10:3");
        verify(scheduler, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void 앞_순번이_비면_붙잡았다가_오면_순서대로_보낸다() {
        offer(ROOM_A, 1);
        offer(ROOM_A, 3);
        assertThat(sent).containsExactly("10:1");

        offer(ROOM_A, 2);
        assertThat(sent).containsExactly("10:1", "10:2", "10:3");
    }

    @Test
    void 빈_순번을_상한까지_기다려도_안_오면_건너뛰고_보낸다() {
        offer(ROOM_A, 1);
        offer(ROOM_A, 3);

        runGapTimeout();

        assertThat(sent).containsExactly("10:1", "10:3");
        assertThat(registry.counter("chat.order.skipped").count()).isEqualTo(1);
    }

    @Test
    void 건너뛴_뒤_늦게_온_순번은_보내지_않는다() {
        offer(ROOM_A, 1);
        offer(ROOM_A, 3);
        runGapTimeout();

        offer(ROOM_A, 2);

        assertThat(sent).containsExactly("10:1", "10:3");
        assertThat(registry.counter("chat.order.late.dropped").count()).isEqualTo(1);
    }

    @Test
    void 빈_순번이_채워진_뒤_늦게_울린_타이머는_아무것도_하지_않는다() {
        offer(ROOM_A, 1);
        offer(ROOM_A, 3);
        offer(ROOM_A, 2);

        runGapTimeout();   // 2 를 기다리던 타이머가 취소되지 못하고 뒤늦게 돈 경우
        offer(ROOM_A, 4);

        assertThat(sent).containsExactly("10:1", "10:2", "10:3", "10:4");
        assertThat(registry.counter("chat.order.skipped").count()).isZero();
    }

    @Test
    void 한_방이_기다려도_다른_방은_막히지_않는다() {
        offer(ROOM_A, 1);
        offer(ROOM_A, 3);
        offer(ROOM_B, 7);
        offer(ROOM_B, 8);

        assertThat(sent).containsExactly("10:1", "20:7", "20:8");
    }

    @Test
    void 처음_보는_방은_받은_첫_순번부터_시작한다() {
        offer(ROOM_A, 42);

        assertThat(sent).containsExactly("10:42");
    }

    @Test
    void 붙잡았던_메시지가_보내지면_기다린_시간을_남긴다() {
        offer(ROOM_A, 1);
        offer(ROOM_A, 3);
        offer(ROOM_A, 2);

        assertThat(registry.timer("chat.order.wait").count()).isEqualTo(1);
        assertThat(registry.counter("chat.order.held").count()).isEqualTo(1);
    }

    private void offer(Long room, long seq) {
        orderer.offer(room, seq, () -> sent.add(room + ":" + seq));
    }

    private void runGapTimeout() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.captor();
        verify(scheduler, atLeastOnce()).schedule(task.capture(), any(Instant.class));
        task.getValue().run();
    }
}
