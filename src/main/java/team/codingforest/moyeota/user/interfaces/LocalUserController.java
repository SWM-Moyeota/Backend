package team.codingforest.moyeota.user.interfaces;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.codingforest.moyeota.user.api.CurrentUser;
import team.codingforest.moyeota.user.application.AuthService;
import team.codingforest.moyeota.user.application.LocalUserService;
import team.codingforest.moyeota.user.application.dto.UserRegisterRequest;
import team.codingforest.moyeota.user.application.dto.UserResponse;

@RestController
@RequestMapping("/api/v1/local")
@RequiredArgsConstructor
public class LocalUserController {
    private final LocalUserService service;
    private final AuthService authService;

    @PostMapping("/users")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request.toCommand()));
    }

    @GetMapping("/users/info")
    public ResponseEntity<?> getProfile(@CurrentUser Long userId) {
        return ResponseEntity.ok(service.getProfile(userId));
    }
}