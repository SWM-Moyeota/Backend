package team.codingforest.moyeota.chat.infra;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.chat.app.dto.ChatMessageResult;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRedisSubscriber implements MessageListener {

    private static final String ROOM_DESTINATION = "/sub/chat-rooms/";

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        try {
            ChatMessageResult result = objectMapper.readValue(message.getBody(), ChatMessageResult.class);

            messagingTemplate.convertAndSend(ROOM_DESTINATION + result.chatRoomId(), result);
        } catch (Exception e) {
            log.error("Redis 메시지 처리 실패" , e);
        }
    }
}
