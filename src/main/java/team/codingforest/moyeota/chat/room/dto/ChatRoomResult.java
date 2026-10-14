package team.codingforest.moyeota.chat.room.dto;

import team.codingforest.moyeota.chat.room.domain.ChatRoom;
import team.codingforest.moyeota.chat.room.domain.ChatRoomStatus;

import java.time.Instant;

public record ChatRoomResult(
        Long id,
        Long partyId,
        long lastSeq,
        String departure,
        String destination,
        Instant createdAt,
        ChatRoomStatus status
) {
    public static ChatRoomResult from(ChatRoom chatRoom) {
        return new ChatRoomResult(
                chatRoom.getId(),
                chatRoom.getPartyId(),
                chatRoom.getLastSeq(),
                chatRoom.getDeparturePlace(),
                chatRoom.getDestinationPlace(),
                chatRoom.getCreatedAt(),
                chatRoom.getStatus()
        );
    }
}
