package team.codingforest.moyeota.chat.domain;

import lombok.Getter;
import team.codingforest.moyeota.chat.domain.exception.ChatErrorCode;
import team.codingforest.moyeota.chat.domain.exception.ChatException;

import java.time.Instant;

@Getter
public class ChatRoom {
    private final Long id;
    private final Long partyId;
    private final String departure;
    private final String destination;
    private final Instant createdAt;
    private Instant updatedAt;
    private ChatRoomStatus status;

    private ChatRoom(Long id, Long partyId, String departure, String destination, Instant createdAt, Instant updatedAt, ChatRoomStatus status) {
        this.id = id;
        this.partyId = partyId;
        this.departure = departure;
        this.destination = destination;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.status = status;
    }

    public static ChatRoom create(Long partyId, String departure, String destination, Instant now) {
        return new ChatRoom(null, partyId, departure, destination, now, now, ChatRoomStatus.ACTIVE);
    }

    public static ChatRoom restore(Long id, Long partyId, String departure, String destination, Instant createdAt, Instant updatedAt, ChatRoomStatus status) {
        return new ChatRoom(id, partyId, departure, destination, createdAt, updatedAt, status);
    }

    public void close(Instant now) {
        if (status != ChatRoomStatus.ACTIVE) {
            throw new ChatException(ChatErrorCode.CHAT_ROOM_CLOSED);
        }

        status = ChatRoomStatus.CLOSED;
        updatedAt = now;
    }

    public void archive(Instant now) {
        if (status != ChatRoomStatus.CLOSED) {
            throw new ChatException(ChatErrorCode.CHAT_ROOM_INVALID_STATUS);
        }

        status = ChatRoomStatus.ARCHIVED;
        updatedAt = now;
    }

    public void validateCanSend() {
        if (status != ChatRoomStatus.ACTIVE) {
            throw new ChatException(ChatErrorCode.CHAT_ROOM_CLOSED);
        }
    }

    public void validateJoin() {
        if (status != ChatRoomStatus.ACTIVE) {
            throw new ChatException(ChatErrorCode.CHAT_ROOM_CLOSED);
        }
    }
}
