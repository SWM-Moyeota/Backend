package team.codingforest.moyeota.chat.infra.entity;

import jakarta.persistence.*;
import lombok.Getter;
import team.codingforest.moyeota.chat.domain.ChatRoom;
import team.codingforest.moyeota.chat.domain.ChatRoomStatus;

import java.time.Instant;

@Entity
@Getter
@Table(name = "chat_room")
public class ChatRoomEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
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

    protected ChatRoomEntity() {
    }

    private ChatRoomEntity(Long id, Long partyId, String departure, String destination,
                           Instant createdAt, ChatRoomStatus status) {
        this.id = id;
        this.partyId = partyId;
        this.departure = departure;
        this.destination = destination;
        this.createdAt = createdAt;
        this.status = status;
    }

    public static ChatRoomEntity from(ChatRoom chatRoom) {
        return new ChatRoomEntity(
                chatRoom.getId(),
                chatRoom.getPartyId(),
                chatRoom.getDeparture(),
                chatRoom.getDestination(),
                chatRoom.getCreatedAt(),
                chatRoom.getStatus()
        );
    }

    public ChatRoom toDomain() {
        return ChatRoom.restore(
                id,
                partyId,
                departure,
                destination,
                createdAt,
                status
        );
    }
}
