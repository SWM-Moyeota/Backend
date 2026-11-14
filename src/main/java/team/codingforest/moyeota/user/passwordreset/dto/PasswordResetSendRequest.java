package team.codingforest.moyeota.user.passwordreset.dto;

import jakarta.validation.constraints.*;

public record PasswordResetSendRequest(
    @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{3,19}$") String loginId,
    @NotBlank @Pattern(regexp = "^01[016-9]-?\\d{3,4}-?\\d{4}$") String phoneNumber) {
    @Override public String toString() { return "PasswordResetSendRequest[보안 정보 생략]"; }
}
