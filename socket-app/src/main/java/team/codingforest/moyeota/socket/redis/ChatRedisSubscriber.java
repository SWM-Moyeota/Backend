package team.codingforest.moyeota.socket.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import team.codingforest.moyeota.chat.contract.ChatEventEnvelope;
import team.codingforest.moyeota.socket.order.ChatSeqOrderer;
import team.codingforest.moyeota.socket.session.ChatSubscriptionCleaner;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * API 가 Redis 로 발행한 채팅 이벤트를 받아 이 서버에 연결된 구독자에게 전달함
 * payload 는 열어보지 않고 바이트 그대로 봉투의 목적지로 보냄
 * 순번이 실린 새 메시지는 ChatSeqOrderer 를 거쳐 방마다 순번 순서로 보냄
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatSubscriptionCleaner subscriptionCleaner;
    private final ChatSeqOrderer seqOrderer;

    @Override
    public void onMessage(@NonNull Message message, byte[] pattern) {
        try {
            ChatEventEnvelope envelope = objectMapper.readValue(message.getBody(), ChatEventEnvelope.class);

            if (envelope.destination() == null) {
                log.warn("목적지 없는 이벤트를 버림 type={}", envelope.type());
                return;
            }

            if (ChatEventEnvelope.TYPE_ROOM_LEFT.equals(envelope.type())) {
                subscriptionCleaner.unsubscribeRoom(envelope.userId(), envelope.chatRoomId());
            }

            if (envelope.seq() != null && envelope.chatRoomId() != null) {
                seqOrderer.offer(envelope.chatRoomId(), envelope.seq(),
                        () -> deliver(envelope.destination(), envelope.payload()));
                return;
            }

            deliver(envelope.destination(), envelope.payload());
        } catch (Exception e) {
            log.error("Redis 메시지 처리 실패", e);
        }
    }

    private void deliver(String destination, String payload) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        accessor.setLeaveMutable(true);

        messagingTemplate.send(destination, MessageBuilder.createMessage(
                payload.getBytes(StandardCharsets.UTF_8), accessor.getMessageHeaders()));
    }
}
