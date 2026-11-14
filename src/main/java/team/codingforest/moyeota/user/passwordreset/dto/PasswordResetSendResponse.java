package team.codingforest.moyeota.user.passwordreset.dto;

import jakarta.validation.constraints.*;

public record PasswordResetSendResponse(
    String requestId,
    long expiresIn,
    long resendAfter,
    String message) {
    @Override public String toString() { return "PasswordResetSendResponse[보안 정보 생략]"; }
}
