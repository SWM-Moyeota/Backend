package team.codingforest.moyeota.user.passwordreset.dto;

import jakarta.validation.constraints.*;

public record PasswordResetConfirmRequest(
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{43}$") String resetToken,
    @NotBlank @Size(min=8,
    max=64) String newPassword,
    @NotBlank @Size(min=8,
    max=64) String newPasswordConfirm) {
    @Override public String toString() { return "PasswordResetConfirmRequest[보안 정보 생략]"; }
}
