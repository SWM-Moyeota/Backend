package team.codingforest.moyeota.driver.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterDriverRequest(
        @Schema(description = "택시운전자격번호", example = "서울-1234-5678") @NotBlank String qualificationNumber,
        @Schema(example = "국민은행") @NotBlank String bankName,
        @Schema(example = "123-456-789012") @NotBlank String accountNumber,
        @Valid @NotNull VehicleInfo vehicle
) {
    public record VehicleInfo(@Schema(example = "4") @NotNull @Min(2) Integer seats, @Schema(example = "12가3456") @NotBlank String plateNumber, @Schema(example = "중형") @NotBlank String type) {
    }

    public RegisterDriverCommand toCommand(Long userId) {
        return new RegisterDriverCommand(userId, qualificationNumber, bankName, accountNumber,
                vehicle.seats(), vehicle.plateNumber(), vehicle.type());
    }
}