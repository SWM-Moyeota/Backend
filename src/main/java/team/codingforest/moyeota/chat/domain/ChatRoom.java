package team.codingforest.moyeota.chat.domain;

import lombok.Getter;

import java.time.Instant;

@Getter
public class ChatRoom {
    private Long id;
    private ChatRoomStatus status;
    private String departure;
    private String destination;
    private Instant departureTime;
    private Instant createdAt;
}
