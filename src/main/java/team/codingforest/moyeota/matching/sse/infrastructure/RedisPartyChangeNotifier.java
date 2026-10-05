package team.codingforest.moyeota.matching.sse.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.party.domain.PartyChangeNotifier;

@Component
@RequiredArgsConstructor
public class RedisPartyChangeNotifier implements PartyChangeNotifier {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void changed(Long partyId) {
        publish(partyId, "changed");
    }

    @Override
    public void closed(Long partyId) {
        publish(partyId, "closed");
    }

    @Override
    public void left(Long partyId, Long memberId) {
        publish(partyId, PartySseChannel.LEFT_PREFIX + memberId);
    }

    private void publish(Long partyId, String event) {
        redisTemplate.convertAndSend(PartySseChannel.TOPIC, partyId + ":" + event);
    }
}
