package team.codingforest.moyeota.chat.domain;

import lombok.Getter;

import java.time.Instant;

@Getter
public class LocationShareUser {
    private Long id;
    private Long chatRoomId;
    private Long userId;
    private Instant joinedAt;
    private Instant leftAt;
}
