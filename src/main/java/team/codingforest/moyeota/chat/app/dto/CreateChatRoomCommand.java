package team.codingforest.moyeota.chat.app.dto;

public record CreateChatRoomCommand(
        Long partyId,
        String departure,
        String destination
){
}
