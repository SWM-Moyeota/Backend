package team.codingforest.moyeota.chat.app.dto;

import team.codingforest.moyeota.chat.domain.ChatRoomUser;

import java.time.Instant;

public record ChatRoomUserResult(
        Long chatRoomId,
        Long lastReadMessageId,
        boolean notificationMuted,
        Instant joinedAt
) {
    public static ChatRoomUserResult from(ChatRoomUser user) {
        return new ChatRoomUserResult(
                user.getChatRoomId(),
                user.getLastReadMessageId(),
                user.isNotificationMuted(),
                user.getJoinedAt()
        );
    }
}
