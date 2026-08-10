package team.codingforest.moyeota.chat.app.dto;

import java.time.Instant;

public record CreateChatRoomCommand(
        Long partyId,
        String departure,
        String destination
){
}
