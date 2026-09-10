package team.codingforest.moyeota.dispatch.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CompleteRideRequest(@Schema(description = "미터기 요금(원)", example = "12000") @NotNull @Positive Integer fare) {
}
