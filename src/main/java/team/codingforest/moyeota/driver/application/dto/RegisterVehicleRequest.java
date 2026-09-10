package team.codingforest.moyeota.driver.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterVehicleRequest(
        @Schema(description = "승객 좌석 수", example = "4") @Min(2) @NotNull Integer seats,
        @Schema(example = "12가3456") @NotBlank String plateNumber,
        @Schema(description = "차종", example = "중형") @NotBlank String type) {
}