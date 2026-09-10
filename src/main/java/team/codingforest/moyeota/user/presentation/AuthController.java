package team.codingforest.moyeota.user.presentation;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.user.application.AuthService;
import team.codingforest.moyeota.user.application.LocalUserService;
import team.codingforest.moyeota.user.application.dto.NicknameCheckRequest;
import team.codingforest.moyeota.user.application.dto.NicknameCheckResponse;
import team.codingforest.moyeota.user.application.dto.PhoneCheckRequest;
import team.codingforest.moyeota.user.application.dto.PhoneCheckResponse;
import team.codingforest.moyeota.user.application.dto.TokenRequest;
import team.codingforest.moyeota.user.application.dto.TokenResponse;
import team.codingforest.moyeota.user.application.dto.UserLoginRequest;
import team.codingforest.moyeota.user.application.dto.UserRegisterRequest;
import team.codingforest.moyeota.user.application.dto.UserResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LocalUserService localUserService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody UserRegisterRequest request) {
        return localUserService.register(request.toCommand());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody UserLoginRequest request) {
        return authService.login(request.toCommand());
    }

    @PostMapping("/reissue")
    public TokenResponse reissue(@Valid @RequestBody TokenRequest request) {
        return authService.reissue(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody TokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/phone/check")
    public PhoneCheckResponse checkPhone(@Valid @RequestBody PhoneCheckRequest request) {
        return new PhoneCheckResponse(localUserService.existsByPhoneNumber(request.phoneNumber()));
    }

    /** 가입 폼 실시간 중복 확인 - 형식이 틀리면 400(USER107) */
    @PostMapping("/nickname/check")
    public NicknameCheckResponse checkNickname(@Valid @RequestBody NicknameCheckRequest request) {
        return new NicknameCheckResponse(localUserService.existsByNickname(request.nickname()));
    }
}