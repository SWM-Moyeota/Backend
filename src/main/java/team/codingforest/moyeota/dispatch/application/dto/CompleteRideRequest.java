package team.codingforest.moyeota.dispatch.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CompleteRideRequest(@NotNull @Positive Integer fare) {
}
