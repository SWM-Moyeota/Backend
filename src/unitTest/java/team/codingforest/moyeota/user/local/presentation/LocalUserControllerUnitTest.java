package team.codingforest.moyeota.user.local.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.user.local.application.LocalUserService;
import team.codingforest.moyeota.user.local.dto.MyProfileResponse;

import java.time.Instant;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocalUserController.class)
class LocalUserControllerUnitTest extends ControllerUnitTestSupport {
    private static final String URL = "/api/v1/local/users/info";
    private static final Instant RESTRICTED_UNTIL = Instant.parse("2026-11-04T08:30:00Z");

    @MockitoBean
    private LocalUserService service;

    @DisplayName("GET /api/v1/local/users/info - 노쇼 누적 횟수와 제한 끝 시각(ISO-8601)을 함께 준다")
    @Test
    void getProfile_restricted() throws Exception {

        /*
         * settings
         */
        doReturn(new MyProfileResponse(PUBLIC_ID, "길동이", "https://cdn/img.png", 2, RESTRICTED_UNTIL)).when(service).getProfile(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.name").value("길동이"))
                .andExpect(jsonPath("$.imageUrl").value("https://cdn/img.png"))
                .andExpect(jsonPath("$.noShowCount").value(2))
                .andExpect(jsonPath("$.restrictedUntil").value("2026-11-04T08:30:00Z"));
    }

    @DisplayName("GET /api/v1/local/users/info - 제한이 없으면 restrictedUntil 은 null")
    @Test
    void getProfile_notRestricted() throws Exception {

        /*
         * settings
         */
        doReturn(new MyProfileResponse(PUBLIC_ID, "길동이", "https://cdn/img.png", 0, null)).when(service).getProfile(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noShowCount").value(0))
                .andExpect(jsonPath("$.restrictedUntil").value(nullValue()));
    }

    @DisplayName("GET /api/v1/local/users/info - 토큰이 없으면 401")
    @Test
    void getProfile_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(URL))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(service);
    }
}
