package team.codingforest.moyeota.chat.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatRoomRequest(
        @NotNull
        Long partyId,

        @NotBlank
        String departure,

        @NotBlank
        String destination
) {
}
