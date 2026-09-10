package team.codingforest.moyeota.chat.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
    private String departurePlace;

    @Column(nullable = false)
    private String destinationPlace;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ChatRoomStatus status;

    protected ChatRoomEntity() {
    }

    private ChatRoomEntity(Long id, Long partyId, String departurePlace, String destinationPlace,
                           Instant createdAt, Instant updatedAt, ChatRoomStatus status) {
        this.id = id;
        this.partyId = partyId;
        this.departurePlace = departurePlace;
        this.destinationPlace = destinationPlace;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.status = status;
    }

    public static ChatRoomEntity from(ChatRoom chatRoom) {
        return new ChatRoomEntity(
                chatRoom.getId(),
                chatRoom.getPartyId(),
                chatRoom.getDeparture(),
                chatRoom.getDestination(),
                chatRoom.getCreatedAt(),
                chatRoom.getUpdatedAt(),
                chatRoom.getStatus()
        );
    }

    public ChatRoom toDomain() {
        return ChatRoom.restore(
                id,
                partyId,
                departurePlace,
                destinationPlace,
                createdAt,
                updatedAt,
                status
        );
    }
}
