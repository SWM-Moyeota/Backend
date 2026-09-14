package team.codingforest.moyeota.chat.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.chat.config.RedisConfig;
import team.codingforest.moyeota.chat.domain.ChatLocation;
import team.codingforest.moyeota.chat.domain.ChatLocationPublisher;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatLocationRedisPublisher implements ChatLocationPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(Long chatRoomId, UUID publicId, ChatLocation location) {
        try {
            ChatLocationPayload payload = new ChatLocationPayload(
                    chatRoomId, publicId,
                    location.latitude(), location.longitude(), location.measuredAt());

            ChatEventEnvelope envelope = new ChatEventEnvelope(
                    ChatEventEnvelope.TYPE_LOCATION, objectMapper.writeValueAsString(payload));

            redisTemplate.convertAndSend(
                    RedisConfig.CHAT_CHANNEL, objectMapper.writeValueAsString(envelope));
        } catch (JacksonException e) {
            log.error("위치 직렬화 실패 chatRoomId={} publicId={}", chatRoomId, publicId, e);
        }
    }

}
