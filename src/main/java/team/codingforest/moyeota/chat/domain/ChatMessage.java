package team.codingforest.moyeota.chat.domain;

import lombok.Getter;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;

@Getter
public class ChatMessage {
    private final Long id;
    private final Long chatRoomId;
    private final Long userId;
    private final String content;
    private final ChatMessageType type;
    private final Instant createdAt;
    private ChatMessageStatus status;
    private Instant deletedAt;

    private ChatMessage(Long id, Long chatRoomId, Long userId, String content, ChatMessageType type, ChatMessageStatus status, Instant createdAt, Instant deletedAt) {
        this.id = id;
        this.chatRoomId = chatRoomId;
        this.userId = userId;
        this.content = content;
        this.type = type;
        this.status = status;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }

    public static ChatMessage text(Long chatRoomId, Long userId, String content, Instant now) {
        return new ChatMessage(null, chatRoomId, userId, content, ChatMessageType.TEXT, ChatMessageStatus.ACTIVE, now, null);
    }

    public static ChatMessage location(Long chatRoomId, Long userId, String content, Instant now) {
        return new ChatMessage(null, chatRoomId, userId, content, ChatMessageType.LOCATION, ChatMessageStatus.ACTIVE, now, null);
    }

    public static ChatMessage restore(Long id, Long chatRoomId, Long userId, String content, ChatMessageType type, ChatMessageStatus status, Instant createdAt, Instant deletedAt) {
        return new ChatMessage(id, chatRoomId, userId, content, type, status, createdAt, deletedAt);
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
