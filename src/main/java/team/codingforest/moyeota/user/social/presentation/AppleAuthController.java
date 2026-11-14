package team.codingforest.moyeota.user.social.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import team.codingforest.moyeota.user.social.apple.*;
import team.codingforest.moyeota.user.social.dto.SocialLoginResponse;

@io.swagger.v3.oas.annotations.tags.Tag(name = "인증")
@io.swagger.v3.oas.annotations.security.SecurityRequirements   // 전역 Bearer 요구사항 해제
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AppleAuthController {
    private final AppleAuthService apple;
    private final AppleNotificationService notifications;
    @Operation(summary = "애플 로그인 인증 요청", description = "5분 일회성 nonce를 앱의 Apple 요청 nonce에 그대로 지정합니다.")
    @PostMapping("/apple/challenge")
    public AppleAuthService.Challenge challenge() { return apple.challenge(null); }
    @Operation(summary = "애플 로그인", description = "서명·발급자·대상·만료·nonce 및 인증 코드 교환 결과를 검증합니다.")
    @PostMapping("/login/apple")
    public SocialLoginResponse login(@Valid @RequestBody Login request) {
        return apple.login(request.identityToken(), request.authorizationCode(), request.challengeToken(), request.name());
    }
    @Operation(summary = "애플 서버 알림 수신")
    @PostMapping("/apple/notifications")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void notification(@Valid @RequestBody Notification request) { notifications.receive(request.payload()); }
    public record Login(@NotBlank @Size(max=20000) String identityToken, @NotBlank @Size(max=2000) String authorizationCode,
                        @NotBlank @Size(max=200) String challengeToken, @Size(max=100) String name) {
        @Override public String toString() { return "애플 로그인 요청(민감정보 생략)"; }
    }
    public record Notification(@NotBlank @Size(max=20000) String payload) {}
}
