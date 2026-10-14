package team.codingforest.moyeota.chat.message.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import team.codingforest.moyeota.chat.message.domain.ChatMessage;
import team.codingforest.moyeota.chat.message.domain.enums.ChatMessageStatus;
import team.codingforest.moyeota.chat.message.domain.enums.ChatMessageType;

import java.time.Instant;

@Entity
@Getter
@Table(
        name = "chat_message",
        indexes = {
                @Index(name = "idx_chat_message_room_id", columnList = "chat_room_id, id")
        },
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_chat_message_room_seq", columnNames = {"chat_room_id", "seq"})
        }
)
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long chatRoomId;

    // method 함수를 통해서만 숫자 조정 가능
    @Column(nullable = false, updatable = false)
    private Long seq;

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

    private ChatMessageEntity(Long id, Long chatRoomId, Long seq, Long userId, String content,
                              ChatMessageType type, ChatMessageStatus status,
                              Instant createdAt, Instant deletedAt) {
        this.id = id;
        this.chatRoomId = chatRoomId;
        this.seq = seq;
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
                message.getSeq(),
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
                seq,
                userId,
                content,
                type,
                status,
                createdAt,
                deletedAt
        );
    }
}