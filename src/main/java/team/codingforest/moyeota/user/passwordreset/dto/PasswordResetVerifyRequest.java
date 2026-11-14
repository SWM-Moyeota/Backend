package team.codingforest.moyeota.user.passwordreset.dto;

import jakarta.validation.constraints.*;

public record PasswordResetVerifyRequest(
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{43}$") String requestId,
    @NotBlank @Pattern(regexp = "^\\d{6}$") String code) {
    @Override public String toString() { return "PasswordResetVerifyRequest[보안 정보 생략]"; }
}
