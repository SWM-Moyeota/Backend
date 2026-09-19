package team.codingforest.moyeota.rating.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import team.codingforest.moyeota.rating.entity.RatingType;

public record RatingCreateRequest(
        @Schema(description = "매칭 ID", example = "1")
        @NotNull @Positive Long matchId,
        @Schema(description = "평점을 받는 사용자 ID", example = "2")
        @NotNull @Positive Long rateeId,
        @Schema(description = "평점 대상 유형", example = "PASSENGER")
        @NotNull RatingType ratingType,
        @Schema(description = "평점(1~5)", example = "5")
        @NotNull @Min(1) @Max(5) Integer rating
) {
}
