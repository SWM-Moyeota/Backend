package team.codingforest.moyeota.rating.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RatingUpdateRequest(
        @Schema(description = "변경할 평점(1~5)", example = "4")
        @NotNull @Min(1) @Max(5) Integer rating
) {
}
