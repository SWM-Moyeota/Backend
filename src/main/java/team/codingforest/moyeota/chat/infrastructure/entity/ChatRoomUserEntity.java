package team.codingforest.moyeota.chat.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
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
    private Instant createdAt;

    private Instant leftAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ChatRoomUserEntity() {
    }

    private ChatRoomUserEntity(Long userId, Long chatRoomId, Long lastReadMessageId,
                               boolean notificationMuted, Instant createdAt, Instant updatedAt, Instant leftAt) {
        this.userId = userId;
        this.chatRoomId = chatRoomId;
        this.lastReadMessageId = lastReadMessageId;
        this.notificationMuted = notificationMuted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.leftAt = leftAt;
    }

    public static ChatRoomUserEntity from(ChatRoomUser user) {
        return new ChatRoomUserEntity(
                user.getUserId(),
                user.getChatRoomId(),
                user.getLastReadMessageId(),
                user.isNotificationMuted(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLeftAt()
        );
    }

    public ChatRoomUser toDomain() {
        return ChatRoomUser.restore(
                userId,
                chatRoomId,
                lastReadMessageId,
                notificationMuted,
                createdAt,
                updatedAt,
                leftAt
        );
    }
}
