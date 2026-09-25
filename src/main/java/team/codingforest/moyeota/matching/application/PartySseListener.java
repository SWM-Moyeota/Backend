package team.codingforest.moyeota.matching.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import team.codingforest.moyeota.matching.api.PartyClosedEvent;
import team.codingforest.moyeota.matching.api.PartyMemberJoinedEvent;
import team.codingforest.moyeota.matching.api.PartyMemberLeftEvent;
import team.codingforest.moyeota.matching.infrastructure.PartySseChannel;

@Component
@RequiredArgsConstructor
public class PartySseListener {
    private final StringRedisTemplate redisTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PartyMemberJoinedEvent event) {
        publish(event.partyId(), "changed");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PartyMemberLeftEvent event) {
        publish(event.partyId(), "changed");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PartyClosedEvent event) {
        publish(event.partyId(), "closed");
    }

    private void publish(Long partyId, String event) {
        redisTemplate.convertAndSend(PartySseChannel.TOPIC, partyId + ":" + event);
    }
}
