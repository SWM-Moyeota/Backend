package team.codingforest.moyeota.user.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "idToken 은 필수입니다.")
        @Schema(description = "앱이 구글 SDK 로 받은 idToken(JWT)", example = "eyJhbGciOiJSUzI1NiIs...") String idToken
) {
}
