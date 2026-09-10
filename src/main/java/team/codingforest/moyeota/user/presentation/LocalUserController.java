package team.codingforest.moyeota.user.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.user.api.CurrentUser;
import team.codingforest.moyeota.user.application.AuthService;
import team.codingforest.moyeota.user.application.LocalUserService;
import team.codingforest.moyeota.user.application.dto.UserRegisterRequest;
import team.codingforest.moyeota.user.application.dto.UserResponse;

@Tag(name = "내 정보", description = "본인 프로필 조회")
@RestController
@RequestMapping("/api/v1/local")
@RequiredArgsConstructor
public class LocalUserController {
    private final LocalUserService service;
    private final AuthService authService;

    @Hidden   // /api/v1/auth/register 와 중복 - 시큐리티 화이트리스트에 없어 실제로 호출 불가
    @PostMapping("/users")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request.toCommand()));
    }

    @Operation(summary = "내 프로필", description = "publicId 와 표시 이름(닉네임, 없으면 실명)")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "프로필"), @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND(USER103)")})
    @GetMapping("/users/info")
    public ResponseEntity<UserResponse> getProfile(@CurrentUser Long userId) {
        return ResponseEntity.ok(service.getProfile(userId));
    }
}