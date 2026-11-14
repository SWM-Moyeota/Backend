package team.codingforest.moyeota.user.social.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.user.social.application.LoginMethodsService;
import team.codingforest.moyeota.user.social.apple.*;
import team.codingforest.moyeota.user.social.domain.GoogleTokenVerifier;
import team.codingforest.moyeota.user.common.exception.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({LoginMethodsController.class,AppleAuthController.class})
class LoginMethodsControllerTest extends ControllerUnitTestSupport {
    @MockitoBean LoginMethodsService methods;
    @MockitoBean AppleAuthService apple;
    @MockitoBean AppleNotificationService notifications;
    @MockitoBean GoogleTokenVerifier google;
    @Test void 로그인_없는_수단_조회는_거절한다() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/login-methods")).andExpect(status().isUnauthorized()); verifyNoInteractions(methods);
    }
    @Test void 비밀번호_보유_여부와_모든_소셜_수단을_반환한다() throws Exception {
        when(methods.list(USER_ID)).thenReturn(new LoginMethodsService.Methods(true,List.of(new LoginMethodsService.Method("APPLE","REVOKED"),new LoginMethodsService.Method("GOOGLE","ACTIVE"))));
        mockMvc.perform(get("/api/v1/users/me/login-methods").with(loginUser())).andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPassword").value(true)).andExpect(jsonPath("$.methods[0].status").value("REVOKED"));
    }
    @Test void 등록_번호로만_발송하고_응답은_204다() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/login-methods/sms/send").with(loginUser())).andExpect(status().isNoContent()); verify(methods).send(USER_ID);
    }
    @Test void 애플_로그인_challenge는_비로그인으로_요청한다() throws Exception {
        when(apple.challenge(null)).thenReturn(new AppleAuthService.Challenge("challenge","nonce",300));
        mockMvc.perform(post("/api/v1/auth/apple/challenge")).andExpect(status().isOk()).andExpect(jsonPath("$.expiresInSeconds").value(300));
    }
    @Test void 애플_연결_challenge는_현재_사용자에_묶인다() throws Exception {
        when(apple.challenge(USER_ID)).thenReturn(new AppleAuthService.Challenge("challenge","nonce",300));
        mockMvc.perform(post("/api/v1/users/me/login-methods/apple/challenge").with(loginUser())).andExpect(status().isOk()); verify(apple).challenge(USER_ID);
    }
    @Test void 만료된_challenge는_기존_오류_형식이다() throws Exception {
        when(apple.login("identity","code","expired",null)).thenThrow(new UserException(UserErrorCode.SOCIAL_CHALLENGE_INVALID));
        mockMvc.perform(post("/api/v1/auth/login/apple").contentType(MediaType.APPLICATION_JSON).content("{\"identityToken\":\"identity\",\"authorizationCode\":\"code\",\"challengeToken\":\"expired\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("USER407"));
    }
    @Test void 잘못된_문자_코드는_검증_서비스를_호출하지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/login-methods/sms/verify").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"abc\"}"))
                .andExpect(status().isBadRequest()); verifyNoInteractions(methods);
    }
}
