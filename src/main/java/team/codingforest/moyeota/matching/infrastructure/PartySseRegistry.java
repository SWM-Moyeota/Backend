package team.codingforest.moyeota.matching.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PartySseRegistry {
    private static final int HEARTBEAT_MS = 15_000;

    private final Map<Long, Set<SseEmitter>> byParty = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long partyId) {
        SseEmitter emitter = new SseEmitter(0L);
        byParty.computeIfAbsent(partyId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(partyId, emitter));
        emitter.onTimeout(() -> remove(partyId, emitter));
        emitter.onError(e -> remove(partyId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data(partyId));
        } catch (Exception ex) {
            remove(partyId, emitter);
        }

        return emitter;
    }

    public void notify(Long partyId, String event) {
        for(SseEmitter e : byParty.getOrDefault(partyId, Set.of())) {
            try {
                e.send(SseEmitter.event().name(event).data(partyId));
            } catch (Exception ex) {
                remove(partyId, e);
            }
        }
    }

    public void closeAll(Long partyId) {
        Set<SseEmitter> emitters = byParty.remove(partyId);
        if(emitters != null) emitters.forEach(SseEmitter::complete);
    }

    @Scheduled(fixedRate = HEARTBEAT_MS)
    public void heartbeat() {
        byParty.forEach((partyId, emitters) -> emitters.forEach(e -> {
            try {
                e.send(SseEmitter.event().comment("ping"));
            } catch (Exception ex) {
                remove(partyId, e);
            }
        }));
    }

    public int connections() {
        return byParty.values().stream().mapToInt(Set::size).sum();
    }

    private void remove(Long partyId, SseEmitter emitter) {
        Set<SseEmitter> set = byParty.get(partyId);
        if(set != null && set.remove(emitter) && set.isEmpty()) byParty.remove(partyId);
    }
}
