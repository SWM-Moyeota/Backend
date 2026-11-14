package team.codingforest.moyeota.user.social.presentation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.user.api.CurrentUser;
import team.codingforest.moyeota.user.social.application.LoginMethodsService;
import team.codingforest.moyeota.user.social.apple.AppleAuthService;
import team.codingforest.moyeota.user.social.domain.GoogleTokenVerifier;

@RestController
@RequestMapping("/api/v1/users/me/login-methods")
@RequiredArgsConstructor
public class LoginMethodsController {
    private final LoginMethodsService methods;
    private final AppleAuthService apple;
    private final GoogleTokenVerifier google;
    @GetMapping
    public LoginMethodsService.Methods list(@CurrentUser Long userId) { return methods.list(userId); }
    @PostMapping("/sms/send") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void send(@CurrentUser Long userId) { methods.send(userId); }
    @PostMapping("/sms/verify")
    public LoginMethodsService.Verification verify(@CurrentUser Long userId, @Valid @RequestBody Sms request) { return methods.verify(userId, request.code()); }
    @PostMapping("/apple/challenge")
    public AppleAuthService.Challenge challenge(@CurrentUser Long userId) { return apple.challenge(userId); }
    @PostMapping("/google") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void google(@CurrentUser Long userId, @Valid @RequestBody Google request) { methods.link(userId, google.verify(request.idToken()), request.verificationToken(), null); }
    @PostMapping("/apple") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void apple(@CurrentUser Long userId, @Valid @RequestBody Apple request) { apple.link(userId, request.identityToken(), request.authorizationCode(), request.challengeToken(), request.verificationToken()); }
    public record Sms(@NotBlank @Pattern(regexp="[0-9]{6}") String code) {}
    public record Google(@NotBlank @Size(max=20000) String idToken, @NotBlank @Size(max=200) String verificationToken) {
        @Override public String toString() { return "구글 연결 요청(민감정보 생략)"; }
    }
    public record Apple(@NotBlank @Size(max=20000) String identityToken, @NotBlank @Size(max=2000) String authorizationCode,
                        @NotBlank @Size(max=200) String challengeToken, @NotBlank @Size(max=200) String verificationToken) {
        @Override public String toString() { return "애플 연결 요청(민감정보 생략)"; }
    }
}
