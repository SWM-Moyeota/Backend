package team.codingforest.moyeota.driver.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterDriverRequest(
        @NotNull Long userId,
        @NotBlank String qualificationNumber,
        @NotBlank String bankName,
        @NotBlank String accountNumber
) {
    public RegisterDriverCommand toCommand() {
        return new RegisterDriverCommand(userId, qualificationNumber, bankName, accountNumber);
    }
}