package team.codingforest.moyeota.user.auth.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.local.application.LocalUserService;
import team.codingforest.moyeota.user.local.dto.UserRegisterCommand;
import team.codingforest.moyeota.user.local.dto.UserResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *  회원가입의 나이 제한 응답. 앱이 code 문자열로 분기하므로 USER113 이 그대로 나가야 한다
 */
@WebMvcTest(AuthController.class)
class AuthControllerUnitTest extends ControllerUnitTestSupport {
    private static final String URL = "/api/v1/auth/register";

    /** 만 14세 미만(2015 년생)으로만 다른 유효한 가입 요청 */
    private static final String UNDER_AGE_REQUEST = """
            {
                "loginId": "hong123",
                "password": "Passw0rd!",
                "nickname": "길동이",
                "name": "홍길동",
                "birthDate": "2015-01-01T00:00:00Z",
                "phoneNumber": "010-1234-5678",
                "gender": "MALE",
                "email": "hong@example.com",
                "verificationToken": "token"
            }
            """;

    private static final String ADULT_REQUEST = """
            {
                "loginId": "hong123",
                "password": "Passw0rd!",
                "nickname": "길동이",
                "name": "홍길동",
                "birthDate": "2000-01-01T00:00:00Z",
                "phoneNumber": "010-1234-5678",
                "gender": "MALE",
                "email": "hong@example.com",
                "verificationToken": "token"
            }
            """;

    @MockitoBean
    private LocalUserService localUserService;

    @DisplayName("POST /api/v1/auth/register - 만 14세 미만이면 400 UNDER_MINIMUM_AGE(USER113) 다. INVALID_REQUEST 로 뭉개지 않는다")
    @Test
    void register_underMinimumAge() throws Exception {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.UNDER_MINIMUM_AGE)).when(localUserService).register(any(UserRegisterCommand.class));

        /*
         * test
         */
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(UNDER_AGE_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER113"))
                .andExpect(jsonPath("$.message").value(UserErrorCode.UNDER_MINIMUM_AGE.getMessage()));
        verifyNoInteractions(authService);
    }

    @DisplayName("POST /api/v1/auth/register - 나이가 되면 그대로 201 로 가입된다")
    @Test
    void register_created() throws Exception {

        /*
         * settings
         */
        doReturn(new UserResponse(PUBLIC_ID, "길동이", "https://cdn/img.png")).when(localUserService).register(any(UserRegisterCommand.class));

        /*
         * test
         */
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(ADULT_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uuid").value(PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.name").value("길동이"));
    }
}
