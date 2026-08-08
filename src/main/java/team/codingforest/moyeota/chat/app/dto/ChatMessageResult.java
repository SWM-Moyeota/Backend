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
    public static ChatMessageResult from(ChatMessage message) {
        return new ChatMessageResult(
                message.getId(),
                message.getChatRoomId(),
                message.getUserId(),
                message.isDeleted() ? null : message.getContent(),
                message.getType(),
                message.getCreatedAt(),
                message.isDeleted()
        );
    }
}
