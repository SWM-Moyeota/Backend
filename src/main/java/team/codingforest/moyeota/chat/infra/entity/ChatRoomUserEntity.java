package team.codingforest.moyeota.chat.infra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import team.codingforest.moyeota.chat.domain.ChatRoomUser;

import java.time.Instant;

@Entity
@Getter
@Table(name = "chat_room_user")
@IdClass(ChatRoomUserId.class)
public class ChatRoomUserEntity {

    @Id
    private Long userId;

    @Id
    private Long chatRoomId;

    private Long lastReadMessageId;

    @Column(nullable = false)
    private boolean notificationMuted;

    @Column(nullable = false)
    private Instant joinedAt;

    private Instant leftAt;

    protected ChatRoomUserEntity() {
    }

    private ChatRoomUserEntity(Long userId, Long chatRoomId, Long lastReadMessageId,
                               boolean notificationMuted, Instant joinedAt, Instant leftAt) {
        this.userId = userId;
        this.chatRoomId = chatRoomId;
        this.lastReadMessageId = lastReadMessageId;
        this.notificationMuted = notificationMuted;
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
    }

    public static ChatRoomUserEntity from(ChatRoomUser user) {
        return new ChatRoomUserEntity(
                user.getUserId(),
                user.getChatRoomId(),
                user.getLastReadMessageId(),
                user.isNotificationMuted(),
                user.getJoinedAt(),
                user.getLeftAt()
        );
    }

    public ChatRoomUser toDomain() {
        return ChatRoomUser.restore(
                userId,
                chatRoomId,
                lastReadMessageId,
                notificationMuted,
                joinedAt,
                leftAt
        );
    }
}
