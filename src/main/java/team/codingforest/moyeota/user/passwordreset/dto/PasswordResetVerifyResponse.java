package team.codingforest.moyeota.user.passwordreset.dto;

import jakarta.validation.constraints.*;

public record PasswordResetVerifyResponse(
    String resetToken,
    long expiresIn) {
    @Override public String toString() { return "PasswordResetVerifyResponse[보안 정보 생략]"; }
}
