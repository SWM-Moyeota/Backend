package team.codingforest.moyeota.chat.presentation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import team.codingforest.moyeota.chat.domain.ChatLocation;

import java.time.Instant;

public record LocationRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull Instant measuredAt
) {
    public ChatLocation toDomain() {
        return new ChatLocation(latitude, longitude, measuredAt);
    }
}
