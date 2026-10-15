package team.codingforest.moyeota.chat.room.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import team.codingforest.moyeota.chat.room.domain.ChatRoom;
import team.codingforest.moyeota.chat.room.domain.ChatRoomStatus;

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

    // 메시지 순번은 ChatRoomJpaRepository.incrementLastSeq 로만 올림. 방을 통째로 저장하는 close 등이 옛값으로 덮어쓰지 않게 막음
    @Column(nullable = false, updatable = false)
    private Long lastSeq;

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

    private ChatRoomEntity(Long id, Long partyId, Long lastSeq, String departurePlace, String destinationPlace,
                           Instant createdAt, Instant updatedAt, ChatRoomStatus status) {
        this.id = id;
        this.partyId = partyId;
        this.lastSeq = lastSeq;
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
                chatRoom.getLastSeq(),
                chatRoom.getDeparturePlace(),
                chatRoom.getDestinationPlace(),
                chatRoom.getCreatedAt(),
                chatRoom.getUpdatedAt(),
                chatRoom.getStatus()
        );
    }

    public ChatRoom toDomain() {
        return ChatRoom.restore(
                id,
                partyId,
                lastSeq,
                departurePlace,
                destinationPlace,
                createdAt,
                updatedAt,
                status
        );
    }
}
