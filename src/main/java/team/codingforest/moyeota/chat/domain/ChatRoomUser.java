package team.codingforest.moyeota.chat.domain;

import lombok.Getter;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;

@Getter
public class ChatRoomUser {
    private final Long userId;
    private final Long chatRoomId;
    private Long lastReadMessageId;
    private boolean notificationMuted;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant leftAt;

    private ChatRoomUser(Long userId, Long chatRoomId, Long lastReadMessageId,
                         boolean notificationMuted, Instant createdAt, Instant updatedAt, Instant leftAt) {
        this.userId = userId;
        this.chatRoomId = chatRoomId;
        this.lastReadMessageId = lastReadMessageId;
        this.notificationMuted = notificationMuted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.leftAt = leftAt;
    }

    public static ChatRoomUser restore(Long userId, Long chatRoomId, Long lastReadMessageId,
                                       boolean notificationMuted, Instant createdAt, Instant updatedAt, Instant leftAt) {
        return new ChatRoomUser(userId, chatRoomId, lastReadMessageId, notificationMuted, createdAt, updatedAt, leftAt);
    }

    public static ChatRoomUser join(Long userId, Long chatRoomId, Instant now) {
        return new ChatRoomUser(userId, chatRoomId, null, false, now, now,null);
    }


    public void leave(Instant now) {
        if (hasLeft()) {
            throw new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT);
        }
        this.leftAt = now;
        updatedAt = now;
    }

    public void read(Long messageId, Instant now) {
        if (messageId == null) {
            return;
        }
        if (lastReadMessageId == null || messageId > lastReadMessageId) {
            this.lastReadMessageId = messageId;
        }
        updatedAt = now;
    }

    public void muteNotification(Instant now) {
        this.notificationMuted = true;
        updatedAt = now;
    }

    public void unmuteNotification(Instant now) {
        this.notificationMuted = false;
        updatedAt = now;
    }

    public boolean hasLeft() {
        return leftAt != null;
    }
}
