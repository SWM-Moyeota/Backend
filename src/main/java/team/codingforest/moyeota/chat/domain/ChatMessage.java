package team.codingforest.moyeota.chat.domain;

import lombok.Getter;

import java.time.Instant;

@Getter
public class ChatMessage {
    private Long id;
    private Long chatRoomId;
    private Long userId;
    private String content;
    private ChatMessageType type;
    private ChatMessageStatus status;
    private Instant createdAt;
    private Instant deletedAt;
}
