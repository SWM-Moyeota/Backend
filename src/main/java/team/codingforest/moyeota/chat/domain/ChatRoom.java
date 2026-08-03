package team.codingforest.moyeota.chat.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Getter
public class ChatRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ChatStatus status;

    private String departure;

    private String destination;

    private Instant departureTime;

    private Instant createdAt;
}
