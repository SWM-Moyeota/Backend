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
import java.util.UUID;

@Entity
@Getter
@Table(
        name = "chat_message",
        indexes = {
                @Index(name = "idx_chat_message_room_id", columnList = "chat_room_id, id")
        },
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_chat_message_room_seq", columnNames = {"chat_room_id", "seq"}),
            @UniqueConstraint(name = ChatMessageEntity.CLIENT_MESSAGE_ID_UNIQUE, columnNames = {"user_id", "client_message_id"})
        }
)
public class ChatMessageEntity {

    // V40 의 (user_id, client_message_id) 유일 제약 이름 - 저장소가 키 중복을 구분할 때도 씀
    public static final String CLIENT_MESSAGE_ID_UNIQUE = "uk_chat_message_user_client_message";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long chatRoomId;

    // 저장할 때 한 번 정해지는 방 내부 순번 이후 바뀌지 않음
    @Column(nullable = false, updatable = false)
    private Long seq;

    @Column(nullable = false)
    private Long userId;

    // 앱이 메시지마다 만든 UUID - 재시도로 같은 메시지가 두 번 저장되지 않게 사용자 단위로 유일함. 옛 앱은 비어 있음
    @Column(updatable = false)
    private UUID clientMessageId;

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

    private ChatMessageEntity(Long id, Long chatRoomId, Long seq, Long userId, UUID clientMessageId, String content,
                              ChatMessageType type, ChatMessageStatus status,
                              Instant createdAt, Instant deletedAt) {
        this.id = id;
        this.chatRoomId = chatRoomId;
        this.seq = seq;
        this.userId = userId;
        this.clientMessageId = clientMessageId;
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
                message.getClientMessageId(),
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
                clientMessageId,
                content,
                type,
                status,
                createdAt,
                deletedAt
        );
    }
}