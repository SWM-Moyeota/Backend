package team.codingforest.moyeota.user.social.presentation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.user.auth.dto.TokenResponse;
import team.codingforest.moyeota.user.social.application.SocialAuthService;
import team.codingforest.moyeota.user.social.dto.GoogleLoginRequest;
import team.codingforest.moyeota.user.social.dto.SocialLoginResponse;
import team.codingforest.moyeota.user.social.dto.SocialRegisterRequest;

@Tag(name = "인증")
@SecurityRequirements   // 전역 Bearer 요구사항 해제
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class SocialAuthController {

    private final SocialAuthService socialAuthService;

    @Operation(summary = "구글 로그인", description = """
            앱이 구글 SDK 로 받은 idToken 을 보낸다. 서버는 서명·발급자·대상(aud)·만료를 검사한다
            - status=LOGIN: 이미 가입한 회원. accessToken·refreshToken 이 함께 온다
            - status=SIGNUP_REQUIRED: 처음 온 사람. 토큰 대신 signupToken(10분)·email·name 이 온다
              → 문자 인증(POST /sms/send, /sms/verify) 뒤 POST /social/register 로 가입을 끝낸다
            - Android 는 idToken 을 요청할 때 웹 클라이언트 ID 를 서버용 ID 로 지정한다
            """)
    @ApiResponses({@ApiResponse(responseCode = "200", description = "LOGIN 또는 SIGNUP_REQUIRED"), @ApiResponse(responseCode = "400", description = "INVALID_REQUEST - idToken 누락"), @ApiResponse(responseCode = "401", description = "SOCIAL_TOKEN_INVALID(USER401) - 위조·만료·다른 앱용 토큰, 또는 토큰에 email 없음"), @ApiResponse(responseCode = "503", description = "SOCIAL_UNAVAILABLE(USER404)")})
    @PostMapping("/login/google")
    public SocialLoginResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return socialAuthService.loginWithGoogle(request.idToken());
    }

    @Operation(summary = "소셜 회원가입", description = """
            users / social_user / user_profile 한 트랜잭션. 가입과 함께 토큰을 준다(비밀번호가 없어 따로 로그인할 수 없다)
            - signupToken(소셜 로그인 통과)과 verificationToken(휴대폰 인증 통과)이 둘 다 있어야 한다
            - 이메일은 받지 않는다. 소셜 계정의 이메일을 쓴다
            - 만 14세 미만은 가입 불가(UNDER_MINIMUM_AGE, USER113). 로컬 가입과 같은 기준
            - 닉네임 중복 등으로 실패해도 두 토큰은 남아 있어 그대로 다시 시도할 수 있다
            """)
    @ApiResponses({@ApiResponse(responseCode = "201", description = "토큰 쌍"), @ApiResponse(responseCode = "400", description = "입력 검증 실패 / UNDER_MINIMUM_AGE(USER113) / SMS_NOT_VERIFIED(USER207) / INVALID_PHONE_NUMBER(USER105) / INVALID_NICKNAME(USER107)"), @ApiResponse(responseCode = "401", description = "SOCIAL_SIGNUP_EXPIRED(USER402) - 대기표 없음·만료. 구글 로그인부터 다시"), @ApiResponse(responseCode = "409", description = "SOCIAL_ALREADY_REGISTERED(USER403) / PHONE_NUMBER_DUPLICATED(USER104) / NICKNAME_DUPLICATED(USER108)")})
    @PostMapping("/social/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody SocialRegisterRequest request) {
        return socialAuthService.register(request.toCommand());
    }
}
