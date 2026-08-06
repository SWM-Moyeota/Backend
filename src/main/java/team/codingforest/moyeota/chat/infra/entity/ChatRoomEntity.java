package team.codingforest.moyeota.chat.infra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import team.codingforest.moyeota.chat.domain.*;
import java.time.Instant;

@Entity
@Getter
public class ChatRoomEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long partyId;

    @Column(nullable = false)
    private String departure;

    @Column(nullable = false)
    private String destination;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ChatRoomStatus status;

    @Column(nullable = false)
    private Instant departureTime;

    protected ChatRoomEntity() {}

    private ChatRoomEntity(Long partyId, String departure, String destination,
                           Instant createdAt, ChatRoomStatus status, Instant departureTime) {
        this.partyId = partyId;
        this.departure = departure;
        this.destination = destination;
        this.createdAt = createdAt;
        this.status = status;
        this.departureTime = departureTime;
    }

    public static ChatRoomEntity from(ChatRoom chatRoom) {
        return new ChatRoomEntity(
                chatRoom.getPartyId(),
                chatRoom.getDeparture(),
                chatRoom.getDestination(),
                chatRoom.getCreatedAt(),
                chatRoom.getStatus(),
                chatRoom.getDepartureTime()
        );
    }

    public ChatRoom toDomain(){
        return ChatRoom.restore(
                id,
                partyId,
                departure,
                destination,
                createdAt,
                status,
                departureTime
        );
    }
}
