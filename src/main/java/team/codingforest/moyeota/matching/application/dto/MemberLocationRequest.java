package team.codingforest.moyeota.matching.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record MemberLocationRequest(
        @Schema(example = "35.1579") @NotNull Double latitude,
        @Schema(example = "129.0596") @NotNull Double longitude
) {
}
