package team.codingforest.moyeota.chat.application.dto;

import team.codingforest.moyeota.chat.domain.ChatMessage;
import team.codingforest.moyeota.chat.domain.ChatMessageType;
import team.codingforest.moyeota.chat.domain.ChatRoomUser;

import java.time.Instant;
import java.util.UUID;

public record ChatRoomUserResult(
        Long chatRoomId,
        Long lastReadMessageId,
        boolean notificationMuted,
        Instant joinedAt,
        LastMessage lastMessage
) {
    public record LastMessage(
            Long id,
            UUID senderPublicId,
            String content,
            ChatMessageType type,
            Instant createdAt
    ) {
        private static final String DELETED_CONTENT = "삭제된 메시지입니다";

        public static LastMessage from(ChatMessage message, UUID senderPublicId) {
            return new LastMessage(
                    message.getId(),
                    senderPublicId,
                    message.isDeleted() ? DELETED_CONTENT : message.getContent(),
                    message.getType(),
                    message.getCreatedAt()
            );
        }
    }

    public static ChatRoomUserResult from(ChatRoomUser user, LastMessage lastMessage) {
        return new ChatRoomUserResult(
                user.getChatRoomId(),
                user.getLastReadMessageId(),
                user.isNotificationMuted(),
                user.getCreatedAt(),
                lastMessage
        );
    }
}
