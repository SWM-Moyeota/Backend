package team.codingforest.moyeota.chat.app.dto;

import team.codingforest.moyeota.chat.domain.ChatMessage;
import team.codingforest.moyeota.chat.domain.ChatMessageType;

import java.time.Instant;

public record ChatMessageResult (
    Long id,
    Long chatRoomId,
    Long userId,
    String content,
    ChatMessageType type,
    Instant createdAt,
    boolean deleted
) {
    private static final String DELETED_CONTENT = "삭제된 메시지입니다";

    public static ChatMessageResult from(ChatMessage message) {
        return new ChatMessageResult(
                message.getId(),
                message.getChatRoomId(),
                message.getUserId(),
                message.isDeleted() ? DELETED_CONTENT : message.getContent(),
                message.getType(),
                message.getCreatedAt(),
                message.isDeleted()
        );
    }
}
