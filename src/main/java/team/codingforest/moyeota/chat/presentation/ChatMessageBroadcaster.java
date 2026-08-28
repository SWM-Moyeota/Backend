package team.codingforest.moyeota.chat.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import team.codingforest.moyeota.chat.app.dto.ChatMessageResult;
import team.codingforest.moyeota.chat.app.event.ChatMessageDeleteEvent;
import team.codingforest.moyeota.chat.app.event.ChatMessageSentEvent;
import team.codingforest.moyeota.chat.config.RedisConfig;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageBroadcaster {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(ChatMessageSentEvent event) {
        publish(event.result());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageDeleted(ChatMessageDeleteEvent event) {
        publish(event.result());
    }

    private void publish(ChatMessageResult result) {
        try {
            redisTemplate.convertAndSend(
                    RedisConfig.CHAT_CHANNEL,
                    objectMapper.writeValueAsString(result));
        } catch (JacksonException e) {
            log.error("메시지 직렬화 실패 messageId={}", result.id(), e);
        }
    }
}
