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
    private final Instant joinedAt;
    private Instant leftAt;

    private ChatRoomUser(Long userId, Long chatRoomId, Long lastReadMessageId,
                         boolean notificationMuted, Instant joinedAt, Instant leftAt) {
        this.userId = userId;
        this.chatRoomId = chatRoomId;
        this.lastReadMessageId = lastReadMessageId;
        this.notificationMuted = notificationMuted;
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
    }

    public static ChatRoomUser restore(Long userId, Long chatRoomId, Long lastReadMessageId,
                                       boolean notificationMuted, Instant joinedAt, Instant leftAt) {
        return new ChatRoomUser(userId, chatRoomId, lastReadMessageId, notificationMuted, joinedAt, leftAt);
    }

    public static ChatRoomUser join(Long userId, Long chatRoomId, Instant now) {
        return new ChatRoomUser(userId, chatRoomId, null, false, now, null);
    }



    public void leave(Instant now) {
        if (hasLeft()) {
            throw new ChatException(ChatErrorCode.CHAT_NOT_PARTICIPANT);
        }
        this.leftAt = now;
    }

    public void read(Long messageId) {
        if (messageId == null) {
            return;
        }
        if (lastReadMessageId == null || messageId > lastReadMessageId) {
            this.lastReadMessageId = messageId;
        }
    }

    public void muteNotification() {
        this.notificationMuted = true;
    }

    public void unmuteNotification() {
        this.notificationMuted = false;
    }

    public boolean hasLeft() {
        return leftAt != null;
    }
}
