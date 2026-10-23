package team.codingforest.moyeota.matching.sse.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@Slf4j
@Component
public class PartySseRegistry {
    private static final int HEARTBEAT_MS = 30_000;

    private final Map<Long, Set<Subscription>> byParty = new ConcurrentHashMap<>();
    private final PartySseMetrics metrics;

    private record Subscription(Long memberId, SseEmitter emitter) {
    }

    public PartySseRegistry(PartySseMetrics metrics) {
        this.metrics = metrics;
        metrics.bindConnections(this::connections);
    }

    public SseEmitter subscribe(Long partyId, Long memberId, Supplier<Object> snapshot) {
        closeMember(partyId, memberId, "replaced");

        SseEmitter emitter = newEmitter();
        Subscription subscription = new Subscription(memberId, emitter);
        byParty.compute(partyId, (k, set) -> {
            Set<Subscription> subscriptions = set == null ? ConcurrentHashMap.newKeySet() : set;
            subscriptions.add(subscription);
            return subscriptions;
        });

        emitter.onCompletion(() -> remove(partyId, emitter, "complete"));
        emitter.onTimeout(() -> remove(partyId, emitter, "timeout"));
        emitter.onError(e -> remove(partyId, emitter, "error"));

        try {
            emitter.send(SseEmitter.event().name("connected").data(snapshot.get(), MediaType.APPLICATION_JSON));
            metrics.sent("connected", true);
        } catch (Exception ex) {
            metrics.sent("connected", false);
            remove(partyId, emitter, "error");
            completeQuietly(emitter);
        }

        return emitter;
    }

    public void notify(Long partyId, String event, String data) {
        for (Subscription s : byParty.getOrDefault(partyId, Set.of())) {
            try {
                s.emitter().send(SseEmitter.event().name(event).data(data));
                metrics.sent(event, true);
            } catch (Exception ex) {
                metrics.sent(event, false);
                remove(partyId, s.emitter(), "error");
            }
        }
    }

    public void closeAll(Long partyId, String data) {
        Set<Subscription> subscriptions = byParty.remove(partyId);
        if (subscriptions == null) return;

        subscriptions.forEach(s -> {
            try {
                s.emitter().send(SseEmitter.event().name("closed").data(data));
                metrics.sent("closed", true);
                metrics.closed("closed");
            } catch (Exception ex) {
                metrics.sent("closed", false);
                metrics.closed("error");
            }
            completeQuietly(s.emitter());
        });
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
            completeQuietly(s.emitter());
        }
    }

    @Scheduled(fixedRate = HEARTBEAT_MS, scheduler = PartySseSchedulerConfig.SCHEDULER)
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
        AtomicBoolean removed = new AtomicBoolean();

        byParty.computeIfPresent(partyId, (k, set) -> {
            removed.set(set.removeIf(s -> s.emitter() == emitter));
            return set.isEmpty() ? null : set;
        });

        if (removed.get()) metrics.closed(reason);
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ex) {
            log.debug("emitter complete 무시", ex);
        }
    }
}
