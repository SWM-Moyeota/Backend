package team.codingforest.moyeota.socket.order;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 순번 줄 세우기 지표
 * 대기시킨 수·기다린 시간·포기한 순번 수·버린 늦은 순번 수·줄이 있는 방 수
 */
@Component
public class ChatOrderMetrics {

    private final MeterRegistry registry;
    private final Counter held;
    private final Timer waitTime;
    private final Counter skipped;
    private final Counter lateDropped;

    public ChatOrderMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.held = registry.counter("chat.order.held");
        this.waitTime = Timer.builder("chat.order.wait").publishPercentileHistogram().register(registry);
        this.skipped = registry.counter("chat.order.skipped");
        this.lateDropped = registry.counter("chat.order.late.dropped");
    }

    void watchRoomCount(Map<Long, ?> lines) {
        Gauge.builder("chat.order.rooms", lines, Map::size).register(registry);
    }

    void held() {
        held.increment();
    }

    void waited(long nanos) {
        waitTime.record(nanos, TimeUnit.NANOSECONDS);
    }

    void skipped(long count) {
        skipped.increment(count);
    }

    void lateDropped() {
        lateDropped.increment();
    }
}
