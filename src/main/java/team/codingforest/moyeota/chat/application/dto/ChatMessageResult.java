package team.codingforest.moyeota.chat.application.dto;

import team.codingforest.moyeota.chat.domain.ChatMessage;
import team.codingforest.moyeota.chat.domain.ChatMessageType;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResult(
        Long id,
        Long chatRoomId,
        UUID publicId,
        String content,
        ChatMessageType type,
        Instant createdAt,
        boolean deleted
) {
    private static final String DELETED_CONTENT = "삭제된 메시지입니다";

    public static ChatMessageResult from(ChatMessage message, UUID publicId) {
        return new ChatMessageResult(
                message.getId(),
                message.getChatRoomId(),
                publicId,
                message.isDeleted() ? DELETED_CONTENT : message.getContent(),
                message.getType(),
                message.getCreatedAt(),
                message.isDeleted()
        );
    }
}
