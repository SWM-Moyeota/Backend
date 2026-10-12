package team.codingforest.moyeota.matching.sse.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
@Component
public class PartySseRegistry {
    private static final int HEARTBEAT_MS = 30_000;

    private final Map<Long, Set<Subscription>> byParty = new ConcurrentHashMap<>();

    private record Subscription(Long memberId, SseEmitter emitter) {
    }

    public SseEmitter subscribe(Long partyId, Long memberId, Supplier<Object> snapshot) {
        closeMember(partyId, memberId);

        SseEmitter emitter = new SseEmitter(0L);
        byParty.computeIfAbsent(partyId, k -> ConcurrentHashMap.newKeySet()).add(new Subscription(memberId, emitter));
        emitter.onCompletion(() -> remove(partyId, emitter));
        emitter.onTimeout(() -> remove(partyId, emitter));
        emitter.onError(e -> remove(partyId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data(snapshot.get(), MediaType.APPLICATION_JSON));
        } catch (Exception ex) {
            remove(partyId, emitter);
        }

        return emitter;
    }

    public void notify(Long partyId, String event, String data) {
        for (Subscription s : byParty.getOrDefault(partyId, Set.of())) {
            try {
                s.emitter().send(SseEmitter.event().name(event).data(data));
            } catch (Exception ex) {
                remove(partyId, s.emitter());
            }
        }
    }

    public void closeAll(Long partyId) {
        Set<Subscription> subscriptions = byParty.remove(partyId);
        if (subscriptions != null) subscriptions.forEach(s -> s.emitter().complete());
    }

    public void closeMember(Long partyId, Long memberId) {
        Set<Subscription> subscriptions = byParty.get(partyId);
        if (subscriptions == null) return;

        for (Subscription s : subscriptions) {
            if (!s.memberId().equals(memberId)) continue;
            remove(partyId, s.emitter());
            s.emitter().complete();
        }
    }

    @Scheduled(fixedRate = HEARTBEAT_MS)
    public void heartbeat() {
        byParty.forEach((partyId, subscriptions) -> subscriptions.forEach(s -> {
            try {
                s.emitter().send(SseEmitter.event().comment("ping"));
            } catch (Exception ex) {
                remove(partyId, s.emitter());
            }
        }));
    }

    public int connections() {
        return byParty.values().stream().mapToInt(Set::size).sum();
    }

    private void remove(Long partyId, SseEmitter emitter) {
        Set<Subscription> set = byParty.get(partyId);
        if (set != null && set.removeIf(s -> s.emitter() == emitter) && set.isEmpty()) byParty.remove(partyId);
    }
}
