package team.codingforest.moyeota.user.social.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.user.auth.dto.TokenResponse;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.social.application.SocialAuthService;
import team.codingforest.moyeota.user.social.domain.SocialProfile;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;
import team.codingforest.moyeota.user.social.dto.SocialLoginResponse;
import team.codingforest.moyeota.user.social.dto.SocialRegisterCommand;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *  구글 로그인·소셜 가입의 응답 모양. 앱이 status 와 code 문자열로 분기하므로 그대로 나가야 한다.
 *  둘 다 access 토큰 없이 부르는 API 라 인증 헤더를 붙이지 않는다
 */
@WebMvcTest(SocialAuthController.class)
class SocialAuthControllerUnitTest extends ControllerUnitTestSupport {
    private static final String LOGIN_URL = "/api/v1/auth/login/google";
    private static final String REGISTER_URL = "/api/v1/auth/social/register";

    private static final String REGISTER_REQUEST = """
            {
                "signupToken": "signup-token",
                "verificationToken": "sms-token",
                "phoneNumber": "010-1234-5678",
                "nickname": "길동이",
                "name": "홍길동",
                "birthDate": "2000-01-01T00:00:00Z",
                "gender": "MALE"
            }
            """;

    @MockitoBean
    private SocialAuthService socialAuthService;

    @DisplayName("POST /api/v1/auth/login/google - 가입한 회원이면 LOGIN 과 토큰만 나가고 가입용 칸은 빠진다")
    @Test
    void loginWithGoogle_login() throws Exception {
        doReturn(SocialLoginResponse.login(new TokenResponse("access", "refresh"))).when(socialAuthService).loginWithGoogle("id-token");

        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content("{\"idToken\": \"id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGIN"))
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.signupToken").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @DisplayName("POST /api/v1/auth/login/google - 처음 온 사람이면 SIGNUP_REQUIRED 와 대기표가 나가고 토큰 칸은 빠진다")
    @Test
    void loginWithGoogle_signupRequired() throws Exception {
        SocialProfile profile = new SocialProfile(SocialType.GOOGLE, "google-sub-1", "hong@gmail.com", "홍길동");
        doReturn(SocialLoginResponse.signupRequired("signup-token", profile)).when(socialAuthService).loginWithGoogle("id-token");

        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content("{\"idToken\": \"id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.signupToken").value("signup-token"))
                .andExpect(jsonPath("$.email").value("hong@gmail.com"))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @DisplayName("POST /api/v1/auth/login/google - 검사에 실패한 idToken 은 401 SOCIAL_TOKEN_INVALID(USER401) 다")
    @Test
    void loginWithGoogle_invalidToken() throws Exception {
        doThrow(new UserException(UserErrorCode.SOCIAL_TOKEN_INVALID)).when(socialAuthService).loginWithGoogle("forged");

        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content("{\"idToken\": \"forged\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER401"));
    }

    @DisplayName("POST /api/v1/auth/login/google - idToken 이 비면 서비스까지 가지 않고 400 이다")
    @Test
    void loginWithGoogle_blankToken() throws Exception {
        mockMvc.perform(post(LOGIN_URL).contentType(MediaType.APPLICATION_JSON).content("{\"idToken\": \" \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(socialAuthService);
    }

    @DisplayName("POST /api/v1/auth/social/register - 가입되면 201 과 토큰 쌍이 나간다")
    @Test
    void register_created() throws Exception {
        doReturn(new TokenResponse("access", "refresh")).when(socialAuthService).register(any(SocialRegisterCommand.class));

        mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(REGISTER_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"));
    }

    @DisplayName("POST /api/v1/auth/social/register - 대기표가 만료되면 401 SOCIAL_SIGNUP_EXPIRED(USER402) 다")
    @Test
    void register_signupExpired() throws Exception {
        doThrow(new UserException(UserErrorCode.SOCIAL_SIGNUP_EXPIRED)).when(socialAuthService).register(any(SocialRegisterCommand.class));

        mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(REGISTER_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER402"));
    }

    @DisplayName("POST /api/v1/auth/social/register - 휴대폰 인증 토큰이 없으면 서비스까지 가지 않고 400 이다")
    @Test
    void register_missingVerificationToken() throws Exception {
        String withoutVerification = REGISTER_REQUEST.replace("\"verificationToken\": \"sms-token\",", "");

        mockMvc.perform(post(REGISTER_URL).contentType(MediaType.APPLICATION_JSON).content(withoutVerification))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(socialAuthService);
    }
}
