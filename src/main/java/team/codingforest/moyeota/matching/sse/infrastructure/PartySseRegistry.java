package team.codingforest.moyeota.matching.sse.infrastructure;

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
    // 서버는 끊긴 연결을 "보내다 실패"해야 알아챈다. 끊긴 직후의 첫 전송은 성공한 것처럼 보이는 일이 많아
    // 죽은 연결이 heartbeat 한두 번(주기의 1~2배) 동안 Tomcat 연결 자리를 차지한다 - 15초일 때 평균 25초 남아 있었다.
    private static final int HEARTBEAT_MS = 5_000;

    private final Map<Long, Set<Subscription>> byParty = new ConcurrentHashMap<>();

    /** 누가 붙였는지 함께 적는다 - 방을 나간 사람의 연결만 골라 닫으려면 필요하다 */
    private record Subscription(Long memberId, SseEmitter emitter) {}

    public SseEmitter subscribe(Long partyId, Long memberId) {
        SseEmitter emitter = new SseEmitter(0L);
        byParty.computeIfAbsent(partyId, k -> ConcurrentHashMap.newKeySet()).add(new Subscription(memberId, emitter));
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
        for(Subscription s : byParty.getOrDefault(partyId, Set.of())) {
            try {
                s.emitter().send(SseEmitter.event().name(event).data(partyId));
            } catch (Exception ex) {
                remove(partyId, s.emitter());
            }
        }
    }

    public void closeAll(Long partyId) {
        Set<Subscription> subscriptions = byParty.remove(partyId);
        if(subscriptions != null) subscriptions.forEach(s -> s.emitter().complete());
    }

    /**
     *  방을 나간 사람의 연결을 닫는다. 나간 사람은 더 받을 신호가 없는데, 앱이 연결을 끊지 않거나(다른 기기 등)
     *  끊었어도 서버가 heartbeat 실패로 알아챌 때까지 연결이 남는다. 나감은 서버가 아는 사건이라 그때 바로 정리한다.
     */
    public void closeMember(Long partyId, Long memberId) {
        Set<Subscription> subscriptions = byParty.get(partyId);
        if(subscriptions == null) return;

        for(Subscription s : subscriptions) {
            if(!s.memberId().equals(memberId)) continue;
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
        if(set != null && set.removeIf(s -> s.emitter() == emitter) && set.isEmpty()) byParty.remove(partyId);
    }
}
