package team.codingforest.moyeota.chat.message.domain;

import lombok.Getter;
import team.codingforest.moyeota.chat.common.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.common.exception.ChatException;
import team.codingforest.moyeota.chat.message.domain.enums.ChatMessageStatus;
import team.codingforest.moyeota.chat.message.domain.enums.ChatMessageType;

import java.time.Instant;
import java.util.UUID;

@Getter
public class ChatMessage {
    private final Long id;
    private final Long chatRoomId;
    private final Long seq;
    private final Long userId;
    private final UUID clientMessageId;
    private final String content;
    private final ChatMessageType type;
    private final Instant createdAt;
    private ChatMessageStatus status;
    private Instant deletedAt;

    private ChatMessage(Long id, Long chatRoomId, Long seq, Long userId, UUID clientMessageId, String content, ChatMessageType type, ChatMessageStatus status, Instant createdAt, Instant deletedAt) {
        this.id = id;
        this.chatRoomId = chatRoomId;
        this.seq = seq;
        this.userId = userId;
        this.clientMessageId = clientMessageId;
        this.content = content;
        this.type = type;
        this.status = status;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }

    public static ChatMessage text(Long chatRoomId, Long seq, Long userId, String content, Instant now) {
        return text(chatRoomId, seq, userId, null, content, now);
    }

    /**
     * 앱이 붙인 중복 방지 키와 함께 새 문자 메시지를 만듦
     *
     * @param chatRoomId      채팅방 id
     * @param seq             방 안 메시지 순번
     * @param userId          보낸 사람 id
     * @param clientMessageId 앱이 메시지마다 만든 UUID, 없으면 null
     * @param content         메시지 내용
     * @param now             보낸 시각
     * @return 저장 전 메시지
     */
    public static ChatMessage text(Long chatRoomId, Long seq, Long userId, UUID clientMessageId, String content, Instant now) {
        return new ChatMessage(null, chatRoomId, seq, userId, clientMessageId, content, ChatMessageType.TEXT, ChatMessageStatus.ACTIVE, now, null);
    }

    public static ChatMessage location(Long chatRoomId, Long seq, Long userId, String content, Instant now) {
        return new ChatMessage(null, chatRoomId, seq, userId, null, content, ChatMessageType.LOCATION, ChatMessageStatus.ACTIVE, now, null);
    }

    public static ChatMessage restore(Long id, Long chatRoomId, Long seq, Long userId, String content, ChatMessageType type, ChatMessageStatus status, Instant createdAt, Instant deletedAt) {
        return restore(id, chatRoomId, seq, userId, null, content, type, status, createdAt, deletedAt);
    }

    public static ChatMessage restore(Long id, Long chatRoomId, Long seq, Long userId, UUID clientMessageId, String content, ChatMessageType type, ChatMessageStatus status, Instant createdAt, Instant deletedAt) {
        return new ChatMessage(id, chatRoomId, seq, userId, clientMessageId, content, type, status, createdAt, deletedAt);
    }

    public void delete(Instant deletedAt) {
        validateDelete(deletedAt);
        this.status = ChatMessageStatus.DELETED;
        this.deletedAt = deletedAt;
    }

    private void validateDelete(Instant deletedAt) {
        if (isDeleted()) { // 이미 삭제된 경우
            throw new ChatException(ChatErrorCode.CHAT_MESSAGE_ALREADY_DELETED);
        }

        if (deletedAt.isBefore(createdAt)) { // 삭제 시간이 생성 시간보다 빠를 때
            throw new IllegalArgumentException("삭제 시간이 생성 시간보다 빠릅니다");
        }
    }

    public boolean isDeleted() {
        return status == ChatMessageStatus.DELETED;
    }
}
