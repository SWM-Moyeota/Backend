package team.codingforest.moyeota.dispatch.location.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record LocationReportRequest(@Schema(example = "37.4979") @NotNull Double latitude, @Schema(example = "127.0276") @NotNull Double longitude) {
}
