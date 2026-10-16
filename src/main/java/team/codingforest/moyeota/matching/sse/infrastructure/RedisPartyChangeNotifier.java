package team.codingforest.moyeota.matching.sse.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.party.domain.PartyChangeNotifier;
import team.codingforest.moyeota.matching.party.dto.MemberJoined;
import team.codingforest.moyeota.matching.party.dto.MemberLeft;
import team.codingforest.moyeota.matching.sse.dto.PartySseMessage;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class RedisPartyChangeNotifier implements PartyChangeNotifier {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void memberJoined(Long partyId, MemberJoined event) {
        publish(partyId, "member-joined", objectMapper.writeValueAsString(event), null);
    }

    @Override
    public void memberLeft(Long partyId, Long memberId, MemberLeft event) {
        publish(partyId, "member-left", objectMapper.writeValueAsString(event), memberId);
    }

    @Override
    public void closed(Long partyId) {
        publish(partyId, "closed", String.valueOf(partyId), null);
    }

    private void publish(Long partyId, String event, String data, Long leftMemberId) {
        PartySseMessage message = PartySseMessage.from(partyId, event, data, leftMemberId, System.currentTimeMillis());
        redisTemplate.convertAndSend(PartySseChannel.TOPIC, objectMapper.writeValueAsString(message));
    }
}
