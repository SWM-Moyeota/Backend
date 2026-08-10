package team.codingforest.moyeota.chat.app.dto;

import team.codingforest.moyeota.chat.domain.ChatRoom;
import team.codingforest.moyeota.chat.domain.ChatRoomStatus;

import java.time.Instant;

public record ChatRoomResult (
        Long id,
        Long partyId,
        String departure,
        String destination,
        Instant createdAt,
        ChatRoomStatus status,
        Instant departureTime
){
    public static ChatRoomResult from(ChatRoom chatRoom){
        return new ChatRoomResult(
                chatRoom.getId(),
                chatRoom.getPartyId(),
                chatRoom.getDeparture(),
                chatRoom.getDestination(),
                chatRoom.getCreatedAt(),
                chatRoom.getStatus(),
                chatRoom.getDepartureTime()
        );
    }
}
