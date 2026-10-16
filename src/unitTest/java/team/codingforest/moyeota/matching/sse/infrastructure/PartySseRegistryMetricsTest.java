package team.codingforest.moyeota.matching.sse.infrastructure;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MockClock;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class PartySseRegistryMetricsTest {
    private static final Long 방 = 42L;
    private static final Long 다른방 = 43L;
    private static final Long 철수 = 1L;
    private static final Long 영희 = 2L;

    private static final Supplier<Object> SNAPSHOT = () -> "detail";
    private static final List<String> REASONS = List.of("complete", "timeout", "error", "replaced", "left", "closed", "heartbeat");

    private final MockClock clock = new MockClock();
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry(SimpleConfig.DEFAULT, clock);
    private final FakeEmitterRegistry registry = new FakeEmitterRegistry(meters);

    @Test
    void 연결_수는_등록된_연결을_따라간다() {
        registry.subscribe(방, 철수, SNAPSHOT);
        registry.subscribe(방, 영희, SNAPSHOT);
        registry.subscribe(다른방, 철수, SNAPSHOT);
        assertThat(connections()).isEqualTo(3);

        registry.closeMember(방, 영희);
        assertThat(connections()).isEqualTo(2);

        registry.closeAll(방);
        assertThat(connections()).isEqualTo(1);
    }

    @Test
    void 클라이언트가_끊으면_complete_로_한_번_센다() {
        registry.subscribe(방, 철수, SNAPSHOT);

        registry.last().complete();
        registry.last().complete();

        assertClosedOnly("complete", 1);
    }

    @Test
    void 타임아웃은_timeout_으로_한_번_센다() {
        registry.subscribe(방, 철수, SNAPSHOT);

        registry.last().timeout();

        assertClosedOnly("timeout", 1);
    }

    @Test
    void 전송_오류_콜백은_error_로_한_번_센다() {
        registry.subscribe(방, 철수, SNAPSHOT);

        registry.last().error();

        assertClosedOnly("error", 1);
    }

    @Test
    void 같은_사람이_다시_연결하면_옛_연결을_replaced_로_한_번만_센다() {
        registry.subscribe(방, 철수, SNAPSHOT);
        registry.subscribe(방, 철수, SNAPSHOT);

        assertClosedOnly("replaced", 1);
        assertThat(connections()).isEqualTo(1);
    }

    @Test
    void 나간_사람의_연결은_left_로_한_번만_센다() {
        registry.subscribe(방, 철수, SNAPSHOT);
        registry.subscribe(방, 영희, SNAPSHOT);

        registry.closeMember(방, 영희);

        assertClosedOnly("left", 1);
    }

    @Test
    void 방을_닫으면_그_방_연결마다_closed_로_한_번씩_센다() {
        registry.subscribe(방, 철수, SNAPSHOT);
        registry.subscribe(방, 영희, SNAPSHOT);
        registry.subscribe(다른방, 철수, SNAPSHOT);

        registry.closeAll(방);

        assertClosedOnly("closed", 2);
    }

    @Test
    void 알림_전송에_실패하면_error_로_한_번만_센다() {
        registry.subscribe(방, 철수, SNAPSHOT);
        registry.last().failSend = true;

        registry.notify(방, "member-joined", "{}", null);
        registry.last().error();

        assertClosedOnly("error", 1);
        assertThat(connections()).isZero();
    }

    @Test
    void heartbeat_전송에_실패하면_heartbeat_로_한_번만_센다() {
        registry.subscribe(방, 철수, SNAPSHOT);
        registry.last().failSend = true;

        registry.heartbeat();
        registry.last().error();

        assertClosedOnly("heartbeat", 1);
        assertThat(connections()).isZero();
    }

    @Test
    void 첫_스냅샷_전송에_실패하면_error_로_센다() {
        registry.failNextSend = true;

        registry.subscribe(방, 철수, SNAPSHOT);

        assertClosedOnly("error", 1);
        assertThat(sent("connected", "fail")).isEqualTo(1);
    }

    @Test
    void 연결마다_보낸_이벤트를_결과별로_센다() {
        registry.subscribe(방, 철수, SNAPSHOT);
        registry.subscribe(방, 영희, SNAPSHOT);
        registry.last().failSend = true;

        registry.notify(방, "member-left", "{}", null);

        assertThat(sent("connected", "ok")).isEqualTo(2);
        assertThat(sent("member-left", "ok")).isEqualTo(1);
        assertThat(sent("member-left", "fail")).isEqualTo(1);
    }

    @Test
    void heartbeat_는_보낸_이벤트로_세지_않는다() {
        registry.subscribe(방, 철수, SNAPSHOT);

        registry.heartbeat();

        assertThat(meters.find("party.sse.sent").counters()).hasSize(1);
    }

    @Test
    void 구독자가_있으면_발행부터_전달까지_걸린_시간을_기록한다() {
        registry.subscribe(방, 철수, SNAPSHOT);
        registry.subscribe(방, 영희, SNAPSHOT);
        long publishedAt = clock.wallTime();
        clock.add(250, TimeUnit.MILLISECONDS);

        registry.notify(방, "member-joined", "{}", publishedAt);

        Timer delivery = meters.get("party.sse.delivery").timer();
        assertThat(delivery.count()).as("연결 수와 무관하게 메시지당 한 번").isEqualTo(1);
        assertThat(delivery.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(250);
    }

    @Test
    void 구독자가_없으면_전달_시간을_기록하지_않는다() {
        registry.subscribe(다른방, 철수, SNAPSHOT);

        registry.notify(방, "member-joined", "{}", clock.wallTime());

        assertThat(meters.find("party.sse.delivery").timer()).isNull();
    }

    @Test
    void 발행_시각이_없으면_전달_시간을_기록하지_않는다() {
        registry.subscribe(방, 철수, SNAPSHOT);

        registry.notify(방, "member-joined", "{}", null);

        assertThat(meters.find("party.sse.delivery").timer()).isNull();
    }

    private double connections() {
        return meters.get("party.sse.connections").gauge().value();
    }

    private double sent(String event, String result) {
        Counter counter = meters.find("party.sse.sent").tags("event", event, "result", result).counter();
        return counter == null ? 0 : counter.count();
    }

    private void assertClosedOnly(String reason, int expected) {
        for (String r : REASONS) {
            Counter counter = meters.find("party.sse.closed").tag("reason", r).counter();
            double count = counter == null ? 0 : counter.count();
            assertThat(count).as("reason=" + r).isEqualTo(r.equals(reason) ? expected : 0);
        }
    }

    static class FakeEmitterRegistry extends PartySseRegistry {
        final List<FakeEmitter> emitters = new ArrayList<>();
        boolean failNextSend;

        FakeEmitterRegistry(SimpleMeterRegistry meters) {
            super(meters);
        }

        @Override
        SseEmitter newEmitter() {
            FakeEmitter emitter = new FakeEmitter();
            emitter.failSend = failNextSend;
            failNextSend = false;
            emitters.add(emitter);
            return emitter;
        }

        FakeEmitter last() {
            return emitters.getLast();
        }
    }

    static class FakeEmitter extends SseEmitter {
        boolean failSend;
        private Runnable completion = () -> {};
        private Runnable timeout = () -> {};
        private Consumer<Throwable> error = e -> {};

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failSend) throw new IOException("broken pipe");
        }

        @Override
        public void onCompletion(Runnable callback) {
            completion = callback;
        }

        @Override
        public void onTimeout(Runnable callback) {
            timeout = callback;
        }

        @Override
        public void onError(Consumer<Throwable> callback) {
            error = callback;
        }

        @Override
        public void complete() {
            completion.run();
        }

        void timeout() {
            timeout.run();
            completion.run();
        }

        void error() {
            error.accept(new IOException("broken pipe"));
            completion.run();
        }
    }
}
