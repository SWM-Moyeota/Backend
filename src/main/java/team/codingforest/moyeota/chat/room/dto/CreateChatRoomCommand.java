package team.codingforest.moyeota.chat.room.dto;

public record CreateChatRoomCommand(
        Long partyId,
        String departure,
        String destination
) {
}
