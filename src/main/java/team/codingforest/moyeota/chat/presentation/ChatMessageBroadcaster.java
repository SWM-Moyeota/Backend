package team.codingforest.moyeota.chat.presentation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import team.codingforest.moyeota.chat.application.event.ChatMessageDeleteEvent;
import team.codingforest.moyeota.chat.application.event.ChatMessageSentEvent;
import team.codingforest.moyeota.chat.application.event.ChatRoomJoinedEvent;
import team.codingforest.moyeota.chat.application.event.ChatRoomLeftEvent;
import team.codingforest.moyeota.chat.config.RedisConfig;
import team.codingforest.moyeota.chat.domain.ChatMember;
import team.codingforest.moyeota.chat.domain.MemberProvider;
import team.codingforest.moyeota.chat.domain.enums.MemberChangeType;
import team.codingforest.moyeota.chat.infrastructure.ChatEventEnvelope;
import team.codingforest.moyeota.chat.infrastructure.ChatRoomLeftResult;
import team.codingforest.moyeota.chat.infrastructure.MemberChangedPayload;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageBroadcaster {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MemberProvider memberProvider;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(ChatMessageSentEvent event) {
        publish(ChatEventEnvelope.TYPE_MESSAGE, event.result());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageDeleted(ChatMessageDeleteEvent event) {
        publish(ChatEventEnvelope.TYPE_MESSAGE, event.result());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomJoined(ChatRoomJoinedEvent event) {
        publish(ChatEventEnvelope.TYPE_MEMBER, memberChanged(event.chatRoomId(), findMember(event.userId()), MemberChangeType.JOINED));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRoomLeft(ChatRoomLeftEvent event) {
        ChatMember member = findMember(event.userId());
        UUID publicId = event.publicId() != null
                ? event.publicId()
                : (member == null ? null : member.publicId());

        publish(ChatEventEnvelope.TYPE_ROOM_LEFT, new ChatRoomLeftResult(event.userId(), publicId, event.chatRoomId()));
        publish(ChatEventEnvelope.TYPE_MEMBER, memberChanged(event.chatRoomId(), member, MemberChangeType.LEFT));
    }

    private void publish(String type, Object payload) {
        try {
            ChatEventEnvelope envelope = new ChatEventEnvelope(
                    type, objectMapper.writeValueAsString(payload));

            redisTemplate.convertAndSend(
                    RedisConfig.CHAT_CHANNEL,
                    objectMapper.writeValueAsString(envelope));
        } catch (JacksonException e) {
            log.error("메시지 직렬화 실패 type={}", type, e);
        }
    }

    private ChatMember findMember(Long userId) {
        return memberProvider.findMembers(List.of(userId)).get(userId);
    }

    private MemberChangedPayload memberChanged(Long chatRoomId, ChatMember member, MemberChangeType type) {
        return new MemberChangedPayload(
                chatRoomId,
                member == null ? null : member.publicId(),
                member == null ? null : member.nickname(),
                member == null ? null : member.imageUrl(),
                type);
    }
}
