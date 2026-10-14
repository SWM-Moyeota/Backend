package team.codingforest.moyeota.matching.sse.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.sse.dto.PartySseMessage;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartySseChannel implements MessageListener {
    public static final String TOPIC = "party:events";

    private final ObjectMapper objectMapper;
    private final PartySseRegistry partySseRegistry;

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        try {
            PartySseMessage m = objectMapper.readValue(message.getBody(), PartySseMessage.class);

            if(m.leftMemberId() != null) {
                partySseRegistry.closeMember(m.partyId(), m.leftMemberId());
            }

            partySseRegistry.notify(m.partyId(), m.event(), m.data());

            if("closed".equals(m.event())) {
                partySseRegistry.closeAll(m.partyId());
            }
        } catch (Exception e) {
            log.warn("SSE 처리 실패", e);
        }
    }
}
