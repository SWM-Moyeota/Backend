package team.codingforest.moyeota.user.social.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import team.codingforest.moyeota.user.auth.dto.TokenResponse;
import team.codingforest.moyeota.user.social.domain.SocialProfile;

/**
 * 소셜 로그인 결과. status 에 따라 채워지는 칸이 다르고, 안 쓰는 칸은 응답에서 빠진다
 * - LOGIN: accessToken, refreshToken
 * - SIGNUP_REQUIRED: signupToken, email, name
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SocialLoginResponse(
        @Schema(description = "LOGIN 이면 홈으로, SIGNUP_REQUIRED 면 휴대폰 인증 화면으로") SocialLoginStatus status,
        @Schema(description = "LOGIN 일 때만") String accessToken,
        @Schema(description = "LOGIN 일 때만") String refreshToken,
        @Schema(description = "SIGNUP_REQUIRED 일 때만. 가입 대기표, 10분간 유효") String signupToken,
        @Schema(description = "SIGNUP_REQUIRED 일 때만. 소셜 계정 이메일 - 가입 화면 표시용") String email,
        @Schema(description = "SIGNUP_REQUIRED 일 때만. 소셜 계정 이름 - 가입 화면에 미리 채우는 용도, 없을 수 있음") String name
) {
    public static SocialLoginResponse login(TokenResponse tokens) {
        return new SocialLoginResponse(SocialLoginStatus.LOGIN, tokens.accessToken(), tokens.refreshToken(), null, null, null);
    }

    public static SocialLoginResponse signupRequired(String signupToken, SocialProfile profile) {
        return new SocialLoginResponse(SocialLoginStatus.SIGNUP_REQUIRED, null, null, signupToken, profile.email(), profile.name());
    }
}
