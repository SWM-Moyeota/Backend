package team.codingforest.moyeota.chat.application.dto;

public record CreateChatRoomCommand(
        Long partyId,
        String departure,
        String destination
) {
}
