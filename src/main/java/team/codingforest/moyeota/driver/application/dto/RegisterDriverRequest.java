package team.codingforest.moyeota.driver.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterDriverRequest(
        @NotBlank String qualificationNumber,
        @NotBlank String bankName,
        @NotBlank String accountNumber,
        @Valid @NotNull VehicleInfo vehicle
) {
    public record VehicleInfo(@NotNull @Min(2) Integer seats, @NotBlank String plateNumber, @NotBlank String type) {
    }

    public RegisterDriverCommand toCommand(Long userId) {
        return new RegisterDriverCommand(userId, qualificationNumber, bankName, accountNumber,
                vehicle.seats(), vehicle.plateNumber(), vehicle.type());
    }
}