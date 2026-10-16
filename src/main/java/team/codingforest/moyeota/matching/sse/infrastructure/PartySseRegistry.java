package team.codingforest.moyeota.matching.sse.infrastructure;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
@Component
public class PartySseRegistry {
    private static final int HEARTBEAT_MS = 30_000;
    private static final String CLOSED_METRIC = "party.sse.closed";
    private static final String SENT_METRIC = "party.sse.sent";
    private static final String DELIVERY_METRIC = "party.sse.delivery";

    private final Map<Long, Set<Subscription>> byParty = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    private record Subscription(Long memberId, SseEmitter emitter) {
    }

    public PartySseRegistry(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("party.sse.connections", this, PartySseRegistry::connections).register(meterRegistry);
    }

    public SseEmitter subscribe(Long partyId, Long memberId, Supplier<Object> snapshot) {
        closeMember(partyId, memberId, "replaced");

        SseEmitter emitter = newEmitter();
        byParty.computeIfAbsent(partyId, k -> ConcurrentHashMap.newKeySet()).add(new Subscription(memberId, emitter));
        emitter.onCompletion(() -> remove(partyId, emitter, "complete"));
        emitter.onTimeout(() -> remove(partyId, emitter, "timeout"));
        emitter.onError(e -> remove(partyId, emitter, "error"));

        try {
            emitter.send(SseEmitter.event().name("connected").data(snapshot.get(), MediaType.APPLICATION_JSON));
            sent("connected", "ok");
        } catch (Exception ex) {
            sent("connected", "fail");
            remove(partyId, emitter, "error");
        }

        return emitter;
    }

    public void notify(Long partyId, String event, String data, @Nullable Long publishedAt) {
        boolean delivered = false;
        for (Subscription s : byParty.getOrDefault(partyId, Set.of())) {
            delivered = true;
            try {
                s.emitter().send(SseEmitter.event().name(event).data(data));
                sent(event, "ok");
            } catch (Exception ex) {
                sent(event, "fail");
                remove(partyId, s.emitter(), "error");
            }
        }
        if (delivered && publishedAt != null) {
            long elapsed = meterRegistry.config().clock().wallTime() - publishedAt;
            meterRegistry.timer(DELIVERY_METRIC).record(Duration.ofMillis(elapsed));
        }
    }

    public void closeAll(Long partyId) {
        Set<Subscription> subscriptions = byParty.remove(partyId);
        if (subscriptions == null) return;

        for (Subscription s : subscriptions) {
            if (subscriptions.remove(s)) closed("closed");
            s.emitter().complete();
        }
    }

    public void closeMember(Long partyId, Long memberId) {
        closeMember(partyId, memberId, "left");
    }

    private void closeMember(Long partyId, Long memberId, String reason) {
        Set<Subscription> subscriptions = byParty.get(partyId);
        if (subscriptions == null) return;

        for (Subscription s : subscriptions) {
            if (!s.memberId().equals(memberId)) continue;
            remove(partyId, s.emitter(), reason);
            s.emitter().complete();
        }
    }

    @Scheduled(fixedRate = HEARTBEAT_MS)
    public void heartbeat() {
        byParty.forEach((partyId, subscriptions) -> subscriptions.forEach(s -> {
            try {
                s.emitter().send(SseEmitter.event().comment("ping"));
            } catch (Exception ex) {
                remove(partyId, s.emitter(), "heartbeat");
            }
        }));
    }

    public int connections() {
        return byParty.values().stream().mapToInt(Set::size).sum();
    }

    SseEmitter newEmitter() {
        return new SseEmitter(0L);
    }

    private void remove(Long partyId, SseEmitter emitter, String reason) {
        Set<Subscription> set = byParty.get(partyId);
        if (set == null) return;

        for (Subscription s : set) {
            if (s.emitter() != emitter || !set.remove(s)) continue;
            if (set.isEmpty()) byParty.remove(partyId);
            closed(reason);
            return;
        }
    }

    private void closed(String reason) {
        meterRegistry.counter(CLOSED_METRIC, "reason", reason).increment();
    }

    private void sent(String event, String result) {
        meterRegistry.counter(SENT_METRIC, "event", event, "result", result).increment();
    }
}
