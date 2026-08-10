package team.codingforest.moyeota.chat.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ChatRoomRequest(
        @NotNull
        Long partyId,

        @NotBlank
        String departure,

        @NotBlank
        String destination,

        Instant departureTime
) {
}
