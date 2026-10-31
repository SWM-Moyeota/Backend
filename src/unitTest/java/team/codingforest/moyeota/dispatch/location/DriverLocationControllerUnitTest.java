package team.codingforest.moyeota.dispatch.location;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.DriverSetting;
import team.codingforest.moyeota.driver.domain.DriverStatus;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DriverLocationController.class)
class DriverLocationControllerUnitTest extends ControllerUnitTestSupport {
    private static final String VALID_LOCATION = """
            {
                "latitude": 37.4979,
                "longitude": 127.0276
            }
            """;

    @MockitoBean
    private DriverLocationService service;

    @DisplayName("POST /api/v1/dispatch/online - 성공하면 토큰의 기사 번호와 위도·경도로 등록하고 204")
    @Test
    void goOnline() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/online").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_LOCATION))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).goOnline(eq(DRIVER_ID), eq(37.4979), eq(127.0276));
    }

    @DisplayName("POST /api/v1/dispatch/online - 좌표가 0 이어도 누락이 아니므로 성공")
    @Test
    void goOnline_zeroCoordinates() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/online").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "latitude": 0,
                                    "longitude": 0
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(service).goOnline(eq(DRIVER_ID), eq(0.0), eq(0.0));
    }

    @DisplayName("POST /api/v1/dispatch/online - 토큰이 없으면 401")
    @Test
    void goOnline_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/online")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_LOCATION))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UserErrorCode.UNAUTHORIZED.getCode()));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/online - 토큰이 위조됐으면 401")
    @Test
    void goOnline_invalidToken() throws Exception {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.TOKEN_INVALID)).when(authService).authenticate(eq("forged-token"));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/online").header(HttpHeaders.AUTHORIZATION, "Bearer forged-token")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_LOCATION))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UserErrorCode.TOKEN_INVALID.getCode()));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/online - 기사로 등록되지 않은 유저면 404")
    @Test
    void goOnline_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/online").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_LOCATION))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/online - 탈퇴한 기사면 404")
    @Test
    void goOnline_withdrawnDriver() throws Exception {

        /*
         * settings
         */
        Driver withdrawn = Driver.load(DRIVER_ID, USER_ID, "서울-1234-5678", null, null, null,
                DriverSetting.load(false), DriverStatus.VERIFIED, null, Instant.parse("2026-01-01T00:00:00Z"));

        doReturn(Optional.of(withdrawn)).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/online").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_LOCATION))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/online - 기사 미등록이면 본문이 잘못돼도 400 이 아니라 404")
    @Test
    void goOnline_driverNotRegisteredWithInvalidBody() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/online").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "latitude": null,
                                    "longitude": null
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/online - 위도가 없으면 400")
    @Test
    void goOnline_missingLatitude() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/online").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "longitude": 127.0276
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("latitude")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/online - 경도가 없으면 400")
    @Test
    void goOnline_missingLongitude() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/online").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "latitude": 37.4979
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("longitude")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/online - 위도가 null 이면 400")
    @Test
    void goOnline_nullLatitude() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/online").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "latitude": null,
                                    "longitude": 127.0276
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/online - 위도가 숫자가 아니면 400")
    @Test
    void goOnline_nonNumericLatitude() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/online").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "latitude": "abc",
                                    "longitude": 127.0276
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/online - 본문이 없으면 400")
    @Test
    void goOnline_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/online").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/online - JSON 형식이 깨졌으면 400")
    @Test
    void goOnline_malformedJson() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/online").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "latitude": 37.4979,
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/online - JSON 이 아닌 본문이면 415")
    @Test
    void goOnline_unsupportedContentType() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/online").with(loginDriver())
                        .contentType(MediaType.TEXT_PLAIN).content("37.4979,127.0276"))

        /*
         * verify
         */
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/location - 성공하면 토큰의 기사 번호와 위도·경도로 갱신하고 204")
    @Test
    void report() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/location").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_LOCATION))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).report(eq(DRIVER_ID), eq(37.4979), eq(127.0276));
    }

    @DisplayName("POST /api/v1/dispatch/location - 토큰이 없으면 401")
    @Test
    void report_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/location")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_LOCATION))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/location - 기사로 등록되지 않은 유저면 404")
    @Test
    void report_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/location").with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_LOCATION))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/location - 위도가 없으면 400")
    @Test
    void report_missingLatitude() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/location").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "longitude": 127.0276
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("latitude")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/location - 경도가 없으면 400")
    @Test
    void report_missingLongitude() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/location").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "latitude": 37.4979
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("longitude")));
        verifyNoInteractions(service);
    }

    @DisplayName("POST /api/v1/dispatch/location - 본문이 없으면 400")
    @Test
    void report_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/location").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @DisplayName("DELETE /api/v1/dispatch/online - 성공하면 토큰의 기사 번호로 종료하고 204")
    @Test
    void goOffline() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete("/api/v1/dispatch/online").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).goOffline(eq(DRIVER_ID));
    }

    @DisplayName("DELETE /api/v1/dispatch/online - 토큰이 없으면 401")
    @Test
    void goOffline_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete("/api/v1/dispatch/online"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @DisplayName("DELETE /api/v1/dispatch/online - 기사로 등록되지 않은 유저면 404")
    @Test
    void goOffline_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(delete("/api/v1/dispatch/online").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(service);
    }
}
