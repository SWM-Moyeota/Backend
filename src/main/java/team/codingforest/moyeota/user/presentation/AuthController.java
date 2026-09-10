package team.codingforest.moyeota.user.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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

@Tag(name = "인증", description = "회원가입·로그인·토큰 재발급·로그아웃. 이 그룹은 토큰 없이 호출")
@SecurityRequirements   // 전역 Bearer 요구사항 해제
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final LocalUserService localUserService;

    @Operation(summary = "로컬 회원가입", description = "users / local_user / user_profile 한 트랜잭션. 전화번호는 하이픈 제거 후 저장")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "가입된 유저 publicId·닉네임"), @ApiResponse(responseCode = "400", description = "입력 검증 실패 / INVALID_PHONE_NUMBER(USER105) / INVALID_NICKNAME(USER107)"), @ApiResponse(responseCode = "409", description = "LOGIN_ID_DUPLICATED(USER101) / PHONE_NUMBER_DUPLICATED(USER104) / NICKNAME_DUPLICATED(USER108)")})
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody UserRegisterRequest request) {
        return localUserService.register(request.toCommand());
    }

    @Operation(summary = "로그인", description = "access(30분)·refresh(14일) 발급. 모바일은 Keychain/Keystore 에 보관")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "토큰 쌍"), @ApiResponse(responseCode = "401", description = "LOGIN_FAILED(USER102) - 아이디/비밀번호 불일치(구분 안 함)")})
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody UserLoginRequest request) {
        return authService.login(request.toCommand());
    }

    @Operation(summary = "토큰 재발급", description = "refresh 로 새 쌍 발급. refresh 는 1회용(회전) - 재사용 시 401 이고 재로그인 필요")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "새 토큰 쌍"), @ApiResponse(responseCode = "401", description = "TOKEN_INVALID(USER001) / TOKEN_EXPIRED(USER002) / TOKEN_REUSED(USER004)")})
    @PostMapping("/reissue")
    public TokenResponse reissue(@Valid @RequestBody TokenRequest request) {
        return authService.reissue(request.refreshToken());
    }

    @Operation(summary = "로그아웃", description = "refresh 를 무효화. 이미 무효여도 204(멱등). FCM 토큰은 별도로 DELETE /users/me/fcm-token")
    @ApiResponses({@ApiResponse(responseCode = "204", description = "로그아웃")})
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody TokenRequest request) {
        authService.logout(request.refreshToken());
    }

    @Operation(summary = "전화번호 가입 여부", description = "기사 phone-first 온보딩 1단계. exists=false 면 회원가입으로, true 면 로그인으로")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "exists"), @ApiResponse(responseCode = "400", description = "INVALID_PHONE_NUMBER(USER105)")})
    @PostMapping("/phone/check")
    public PhoneCheckResponse checkPhone(@Valid @RequestBody PhoneCheckRequest request) {
        return new PhoneCheckResponse(localUserService.existsByPhoneNumber(request.phoneNumber()));
    }

    /** 가입 폼 실시간 중복 확인 - 형식이 틀리면 400(USER107) */
    @Operation(summary = "닉네임 사용 가능 여부", description = "가입 폼 실시간 중복 확인. 공백 제거 후 비교")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "exists"), @ApiResponse(responseCode = "400", description = "INVALID_NICKNAME(USER107)")})
    @PostMapping("/nickname/check")
    public NicknameCheckResponse checkNickname(@Valid @RequestBody NicknameCheckRequest request) {
        return new NicknameCheckResponse(localUserService.existsByNickname(request.nickname()));
    }
}