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
    private static final String ROOM_LEFT_DESTINATION = "/queue/room-left";

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        try {
            ChatEventEnvelope envelope = objectMapper.readValue(message.getBody(), ChatEventEnvelope.class);

            switch (envelope.type()) {
                case ChatEventEnvelope.TYPE_MESSAGE -> handleMessage(envelope.payload());
                case ChatEventEnvelope.TYPE_ROOM_LEFT -> handleRoomLeft(envelope.payload());
                default -> log.warn("알 수 없는 이벤트 타입 type={}", envelope.type());
            }
        } catch (Exception e) {
            log.error("Redis 메시지 처리 실패", e);
        }
    }

    private void handleMessage(String payload) {
        ChatMessageResult result = objectMapper.readValue(payload, ChatMessageResult.class);
        messagingTemplate.convertAndSend(ROOM_DESTINATION + result.chatRoomId(), result);
    }

    private void handleRoomLeft(String payload) {
        ChatRoomLeftResult result = objectMapper.readValue(payload, ChatRoomLeftResult.class);
        messagingTemplate.convertAndSendToUser(
                String.valueOf(result.userId()), ROOM_LEFT_DESTINATION, result);
    }
}
