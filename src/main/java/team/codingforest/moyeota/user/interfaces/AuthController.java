package team.codingforest.moyeota.user.interfaces;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.user.application.AuthService;
import team.codingforest.moyeota.user.application.LocalUserService;
import team.codingforest.moyeota.user.application.dto.*;

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
}