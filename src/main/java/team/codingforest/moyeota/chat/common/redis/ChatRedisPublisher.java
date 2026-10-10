package team.codingforest.moyeota.chat.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota._config.AsyncConfig;
import team.codingforest.moyeota.chat.common.config.RedisConfig;
import team.codingforest.moyeota.chat.member.domain.ChatMember;
import team.codingforest.moyeota.chat.member.domain.MemberProvider;
import team.codingforest.moyeota.chat.member.domain.enums.MemberChangeType;
import team.codingforest.moyeota.chat.member.infrastructure.ChatRoomLeftResult;
import team.codingforest.moyeota.chat.member.infrastructure.MemberChangedPayload;
import team.codingforest.moyeota.chat.message.dto.ChatMessageResult;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
@Async(AsyncConfig.REALTIME_EXECUTOR)
public class ChatRedisPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MemberProvider memberProvider;

    public void message(ChatMessageResult result) {
        publish(ChatEventEnvelope.TYPE_MESSAGE, result);
    }

    public void memberJoined(Long chatRoomId, Long userId) {
        publish(ChatEventEnvelope.TYPE_MEMBER, memberChanged(chatRoomId, findMember(userId), MemberChangeType.JOINED));
    }

    public void memberLeft(Long chatRoomId, Long userId, UUID publicId) {
        ChatMember member = findMember(userId);
        UUID resolvedPublicId = publicId != null
                ? publicId
                : (member == null ? null : member.publicId());

        publish(ChatEventEnvelope.TYPE_ROOM_LEFT, new ChatRoomLeftResult(userId, resolvedPublicId, chatRoomId));
        publish(ChatEventEnvelope.TYPE_MEMBER, memberChanged(chatRoomId, member, MemberChangeType.LEFT));
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