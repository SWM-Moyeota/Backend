package team.codingforest.moyeota.chat.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatRoomRequest(
        @NotNull(message = "partyId는 필수입니다")
        Long partyId,

        @NotBlank(message = "출발지를 입력해주세요")
        String departure,

        @NotBlank(message = "목적지를 입력해주세요")
        String destination
) {
}
