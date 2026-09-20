package team.codingforest.moyeota.chat.infrastructure.search;

import jakarta.persistence.*;
import lombok.Getter;
import team.codingforest.moyeota.chat.domain.ChatMessage;
import team.codingforest.moyeota.chat.domain.enums.ChatMessageType;
import java.time.Instant;

/** 메시지와 같은 트랜잭션으로 저장하는 불변 색인 스냅샷. id가 Elasticsearch 외부 버전이다. */
@Entity
@Getter
@Table(name = "chat_search_outbox", indexes = @Index(name = "idx_chat_search_outbox_due", columnList = "nextAttemptAt,id"))
public class SearchOutboxEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long messageId;
    @Column(nullable = false) private Long roomId;
    @Column(nullable = false) private Long senderId;
    @Column(length = 1000, nullable = false) private String content;
    @Column(nullable = false) private boolean deleted;
    @Column(nullable = false) private Instant sentAt;
    @Column(nullable = false) private Instant queuedAt;
    @Column(nullable = false) private Instant nextAttemptAt;
    @Column(nullable = false) private int attempts;

    protected SearchOutboxEntry() {}

    public static SearchOutboxEntry of(ChatMessage message) {
        SearchOutboxEntry entry = new SearchOutboxEntry();
        entry.messageId = message.getId();
        entry.roomId = message.getChatRoomId();
        entry.senderId = message.getUserId();
        entry.deleted = message.isDeleted() || message.getType() != ChatMessageType.TEXT;
        entry.content = entry.deleted || message.getContent() == null ? "" : message.getContent();
        entry.sentAt = message.getCreatedAt();
        entry.queuedAt = Instant.now();
        entry.nextAttemptAt = entry.queuedAt;
        return entry;
    }
}
