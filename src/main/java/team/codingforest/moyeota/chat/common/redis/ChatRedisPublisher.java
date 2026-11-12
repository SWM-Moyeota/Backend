package team.codingforest.moyeota.chat.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.chat.contract.ChatChannels;
import team.codingforest.moyeota.chat.contract.ChatDestinations;
import team.codingforest.moyeota.chat.contract.ChatEventEnvelope;
import team.codingforest.moyeota.chat.member.domain.ChatMember;
import team.codingforest.moyeota.chat.member.domain.enums.MemberChangeType;
import team.codingforest.moyeota.chat.member.infrastructure.MemberChangedPayload;
import team.codingforest.moyeota.chat.member.infrastructure.RoomLeftPayload;
import team.codingforest.moyeota.chat.message.dto.ChatMessageResult;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRedisPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 새 메시지를 보냄, 봉투에 방과 순번을 실어 소켓 서버가 방마다 순번 순서로 내보내게 함
     *
     * @param result 저장된 새 메시지
     */
    public void message(ChatMessageResult result) {
        try {
            send(ChatEventEnvelope.message(result.chatRoomId(), result.seq(), objectMapper.writeValueAsString(result)));
        } catch (JacksonException e) {
            log.error("메시지 직렬화 실패 chatRoomId={} seq={}", result.chatRoomId(), result.seq(), e);
        }
    }

    /**
     * 이미 보낸 메시지가 바뀐 것(삭제 등)을 보냄, 지난 순번이라 줄 세우기 없이 바로 전달되게 순번을 싣지 않음
     *
     * @param result 바뀐 메시지
     */
    public void messageChanged(ChatMessageResult result) {
        publish(ChatEventEnvelope.TYPE_MESSAGE, ChatDestinations.room(result.chatRoomId()), result);
    }

    public void memberJoined(Long chatRoomId, ChatMember member) {
        publish(ChatEventEnvelope.TYPE_MEMBER, ChatDestinations.members(chatRoomId),
                memberChanged(chatRoomId, member, MemberChangeType.JOINED));
    }

    public void memberLeft(Long chatRoomId, Long userId, UUID publicId, ChatMember member) {
        UUID resolvedPublicId = publicId != null
                ? publicId
                : (member == null ? null : member.publicId());

        publishRoomLeft(userId, chatRoomId, resolvedPublicId);
        publish(ChatEventEnvelope.TYPE_MEMBER, ChatDestinations.members(chatRoomId),
                memberChanged(chatRoomId, member, MemberChangeType.LEFT));
    }

    private void publish(String type, String destination, Object payload) {
        try {
            send(ChatEventEnvelope.of(type, destination, objectMapper.writeValueAsString(payload)));
        } catch (JacksonException e) {
            log.error("메시지 직렬화 실패 type={}", type, e);
        }
    }

    private void publishRoomLeft(Long userId, Long chatRoomId, UUID publicId) {
        try {
            String payload = objectMapper.writeValueAsString(new RoomLeftPayload(publicId, chatRoomId));
            send(ChatEventEnvelope.roomLeft(userId, chatRoomId, payload));
        } catch (JacksonException e) {
            log.error("퇴장 알림 직렬화 실패 userId={} chatRoomId={}", userId, chatRoomId, e);
        }
    }

    private void send(ChatEventEnvelope envelope) {
        redisTemplate.convertAndSend(ChatChannels.CHAT, objectMapper.writeValueAsString(envelope));
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
