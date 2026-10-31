package team.codingforest.moyeota.dispatch.ride;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.dispatch.exception.DispatchErrorCode;
import team.codingforest.moyeota.dispatch.ride.dto.DriverLocationResponse;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.DriverSetting;
import team.codingforest.moyeota.driver.domain.DriverStatus;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RideController.class)
class RideControllerUnitTest extends ControllerUnitTestSupport {
    private static final Long PARTY_ID = 100L;
    private static final String VALID_FARE = """
            {
                "fare": 12000
            }
            """;

    @MockitoBean
    private RideService rideService;

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/arrive - 성공하면 토큰의 기사 번호로 통보하고 204")
    @Test
    void arrive() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/arrive", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(rideService).arrive(eq(PARTY_ID), eq(DRIVER_ID));
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/arrive - 토큰이 없으면 401")
    @Test
    void arrive_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/arrive", PARTY_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UserErrorCode.UNAUTHORIZED.getCode()));
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/arrive - 기사로 등록되지 않은 유저면 404")
    @Test
    void arrive_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/arrive", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/arrive - 탈퇴한 기사면 404")
    @Test
    void arrive_withdrawnDriver() throws Exception {

        /*
         * settings
         */
        Driver withdrawn = Driver.load(DRIVER_ID, USER_ID, "서울-1234-5678", null, null, null,
                DriverSetting.load(false), DriverStatus.VERIFIED, null, Instant.parse("2026-01-01T00:00:00Z"));

        doReturn(Optional.of(withdrawn)).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/arrive", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/arrive - 숫자가 아닌 partyId 면 400")
    @Test
    void arrive_invalidPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/arrive", "abc").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/arrive - 픽업 대기 중이 아니면 409")
    @Test
    void arrive_notAwaitingPickup() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DispatchErrorCode.NOT_AWAITING_PICKUP)).when(rideService).arrive(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/arrive", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_AWAITING_PICKUP"));
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/board - 성공하면 토큰의 기사 번호로 운행을 시작하고 204")
    @Test
    void board() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/board", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(rideService).board(eq(PARTY_ID), eq(DRIVER_ID));
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/board - 토큰이 없으면 401")
    @Test
    void board_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/board", PARTY_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/board - 기사로 등록되지 않은 유저면 404")
    @Test
    void board_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/board", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/board - 숫자가 아닌 partyId 면 400")
    @Test
    void board_invalidPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/board", "abc").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/board - 배정 기사가 아니면 403")
    @Test
    void board_notAssignedDriver() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(MatchingErrorCode.NOT_ASSIGNED_DRIVER)).when(rideService).board(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/board", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED_DRIVER"));
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/board - 탑승 대기 상태가 아니면 409")
    @Test
    void board_notAwaitingPickup() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(MatchingErrorCode.NOT_AWAITING_PICKUP)).when(rideService).board(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/board", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_AWAITING_PICKUP"));
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/complete - 성공하면 토큰의 기사 번호와 요금으로 종료하고 204")
    @Test
    void complete() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/complete", PARTY_ID).with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_FARE))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(rideService).complete(eq(PARTY_ID), eq(DRIVER_ID), eq(12000));
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/complete - 양수의 최솟값 1 은 성공")
    @Test
    void complete_minFare() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/complete", PARTY_ID).with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "fare": 1
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(rideService).complete(eq(PARTY_ID), eq(DRIVER_ID), eq(1));
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/complete - int 최댓값 요금은 성공")
    @Test
    void complete_maxFare() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/complete", PARTY_ID).with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "fare": 2147483647
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(rideService).complete(eq(PARTY_ID), eq(DRIVER_ID), eq(Integer.MAX_VALUE));
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/complete - 요금이 0 이면 400")
    @Test
    void complete_zeroFare() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/complete", PARTY_ID).with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "fare": 0
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(Matchers.containsString("fare")));
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/complete - 요금이 음수면 400")
    @Test
    void complete_negativeFare() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/complete", PARTY_ID).with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "fare": -1
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/complete - 요금이 null 이면 400")
    @Test
    void complete_nullFare() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/complete", PARTY_ID).with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "fare": null
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/complete - 요금 필드가 없으면 400")
    @Test
    void complete_missingFare() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/complete", PARTY_ID).with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/complete - 요금이 숫자가 아니면 400")
    @Test
    void complete_nonNumericFare() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/complete", PARTY_ID).with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "fare": "abc"
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/complete - 요금이 int 범위를 넘으면 400")
    @Test
    void complete_overflowFare() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/complete", PARTY_ID).with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                    "fare": 2147483648
                                }
                                """))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/complete - 본문이 없으면 400")
    @Test
    void complete_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/complete", PARTY_ID).with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/complete - 토큰이 없으면 401")
    @Test
    void complete_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/complete", PARTY_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_FARE))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/complete - 기사로 등록되지 않은 유저면 404")
    @Test
    void complete_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/complete", PARTY_ID).with(loginUser())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_FARE))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/complete - 숫자가 아닌 partyId 면 400")
    @Test
    void complete_invalidPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/complete", "abc").with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_FARE))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(rideService);
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/complete - 배정 기사가 아니면 403")
    @Test
    void complete_notAssignedDriver() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(MatchingErrorCode.NOT_ASSIGNED_DRIVER)).when(rideService).complete(eq(PARTY_ID), eq(DRIVER_ID), eq(12000));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/complete", PARTY_ID).with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_FARE))

        /*
         * verify
         */
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ASSIGNED_DRIVER"));
    }

    @DisplayName("POST /api/v1/dispatch/rides/{partyId}/complete - 운행 중이 아니면 409")
    @Test
    void complete_notRiding() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(MatchingErrorCode.NOT_RIDING)).when(rideService).complete(eq(PARTY_ID), eq(DRIVER_ID), eq(12000));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/rides/{partyId}/complete", PARTY_ID).with(loginDriver())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_FARE))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_RIDING"));
    }

    @DisplayName("GET /api/v1/dispatch/rides/{partyId} - 토큰의 유저 번호로 조회해 경도·위도를 돌려주고 기사 자격은 보지 않는다")
    @Test
    void getLocation() throws Exception {

        /*
         * settings
         */
        doReturn(new DriverLocationResponse(127.0276, 37.4979)).when(rideService).driverLocation(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/rides/{partyId}", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.longitude").value(127.0276))
                .andExpect(jsonPath("$.latitude").value(37.4979));
        verify(rideService).driverLocation(eq(PARTY_ID), eq(USER_ID));
        verifyNoInteractions(drivers);
    }

    @DisplayName("GET /api/v1/dispatch/rides/{partyId} - 토큰이 없으면 401")
    @Test
    void getLocation_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/rides/{partyId}", PARTY_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(rideService);
    }

    @DisplayName("GET /api/v1/dispatch/rides/{partyId} - 토큰이 만료됐으면 401")
    @Test
    void getLocation_expiredToken() throws Exception {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.TOKEN_EXPIRED)).when(authService).authenticate(eq("expired-token"));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/rides/{partyId}", PARTY_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UserErrorCode.TOKEN_EXPIRED.getCode()));
        verifyNoInteractions(rideService);
    }

    @DisplayName("GET /api/v1/dispatch/rides/{partyId} - 숫자가 아닌 partyId 면 400")
    @Test
    void getLocation_invalidPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/rides/{partyId}", "abc").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(rideService);
    }

    @DisplayName("GET /api/v1/dispatch/rides/{partyId} - 방 구성원이 아니면 403")
    @Test
    void getLocation_notMember() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DispatchErrorCode.NOT_PARTY_MEMBER)).when(rideService).driverLocation(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/rides/{partyId}", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_PARTY_MEMBER"));
    }

    @DisplayName("GET /api/v1/dispatch/rides/{partyId} - 방이 없으면 404")
    @Test
    void getLocation_partyNotFound() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DispatchErrorCode.PARTY_NOT_FOUND)).when(rideService).driverLocation(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/rides/{partyId}", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARTY_NOT_FOUND"));
    }

    @DisplayName("GET /api/v1/dispatch/rides/{partyId} - 기사가 배정 전이면 409")
    @Test
    void getLocation_driverNotAssigned() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DispatchErrorCode.DRIVER_NOT_ASSIGNED)).when(rideService).driverLocation(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/rides/{partyId}", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_ASSIGNED"));
    }
}
