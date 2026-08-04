package team.codingforest.moyeota.chat.infra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import team.codingforest.moyeota.chat.domain.ChatMessageStatus;
import team.codingforest.moyeota.chat.domain.ChatMessageType;

import java.time.Instant;

@Entity
@Getter
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

    @Column(nullable = false)
    private Instant createdAt;

    private Instant deletedAt;
}
