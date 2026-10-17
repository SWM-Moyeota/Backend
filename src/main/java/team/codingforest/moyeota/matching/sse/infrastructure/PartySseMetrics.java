package team.codingforest.moyeota.matching.sse.infrastructure;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class PartySseMetrics {
    private final MeterRegistry registry;

    public void bindConnections(Supplier<Number> connections) {
        Gauge.builder("party.sse.connections", connections).register(registry);
    }

    public void closed(String reason) {
        registry.counter("party.sse.closed", "reason", reason).increment();
    }

    public void sent(String event, boolean ok) {
        registry.counter("party.sse.sent", "event", event, "result", ok ? "ok" : "fail").increment();
    }
}
