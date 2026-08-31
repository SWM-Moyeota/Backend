package team.codingforest.moyeota.chat.infra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import team.codingforest.moyeota.chat.domain.ChatMessage;
import team.codingforest.moyeota.chat.domain.ChatMessageStatus;
import team.codingforest.moyeota.chat.domain.ChatMessageType;

import java.time.Instant;

@Entity
@Getter
@Table(
        name = "chat_message",
        indexes = {
                @Index(name = "idx_chat_message_room_id", columnList = "chat_room_id, id")
        }
)
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long chatRoomId;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 1000)
    private String content;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ChatMessageType type;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ChatMessageStatus status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant deletedAt;

    protected ChatMessageEntity() {
    }

    private ChatMessageEntity(Long id, Long chatRoomId, Long userId, String content,
                              ChatMessageType type, ChatMessageStatus status,
                              Instant createdAt, Instant deletedAt) {
        this.id = id;
        this.chatRoomId = chatRoomId;
        this.userId = userId;
        this.content = content;
        this.type = type;
        this.status = status;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }

    public static ChatMessageEntity from(ChatMessage message) {
        return new ChatMessageEntity(
                message.getId(),
                message.getChatRoomId(),
                message.getUserId(),
                message.getContent(),
                message.getType(),
                message.getStatus(),
                message.getCreatedAt(),
                message.getDeletedAt()
        );
    }

    public ChatMessage toDomain() {
        return ChatMessage.restore(
                getId(),
                chatRoomId,
                userId,
                content,
                type,
                status,
                createdAt,
                deletedAt
        );
    }
}