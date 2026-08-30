package team.codingforest.moyeota.driver.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterVehicleRequest(
        @Min(2) @NotNull Integer seats,
        @NotBlank String plateNumber,
        @NotBlank String type) {
}