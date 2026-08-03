package team.codingforest.moyeota.chat.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Getter
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long chatRoomId;

    private Long userId;

    private String content;

    @Enumerated(EnumType.STRING)
    private ChatMessageType type;

    @Enumerated(EnumType.STRING)
    private ChatMessageStatus status;

    private Instant createdAt;

    private Instant deletedAt;
}
