package team.codingforest.moyeota.chat.domain;

import lombok.Getter;

import java.time.Instant;

@Getter
public class ChatRoomUser {
    private Long userId;
    private Long chatRoomId;
    private Long lastReadMessageId;
    private boolean notificationMuted;
    private Instant joinedAt;
    private Instant leftAt;
}
