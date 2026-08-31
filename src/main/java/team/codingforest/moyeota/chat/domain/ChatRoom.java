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
    private ChatRoomStatus status;

    private ChatRoom(Long id, Long partyId, String departure, String destination, Instant createdAt, ChatRoomStatus status) {
        this.id = id;
        this.partyId = partyId;
        this.departure = departure;
        this.destination = destination;
        this.createdAt = createdAt;
        this.status = status;
    }

    public static ChatRoom create(Long partyId, String departure, String destination, Instant now) {
        return new ChatRoom(null, partyId, departure, destination, now, ChatRoomStatus.ACTIVE);
    }

    public static ChatRoom restore(Long id, Long partyId, String departure, String destination, Instant createdAt, ChatRoomStatus status) {
        return new ChatRoom(id, partyId, departure, destination, createdAt, status);
    }

    public void close() {
        if (status != ChatRoomStatus.ACTIVE) {
            throw new ChatException(ChatErrorCode.CHAT_ROOM_CLOSED);
        }

        status = ChatRoomStatus.CLOSED;
    }

    public void archive() {
        if (status != ChatRoomStatus.CLOSED) {
            throw new ChatException(ChatErrorCode.CHAT_ROOM_INVALID_STATUS);
        }

        status = ChatRoomStatus.ARCHIVED;
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
