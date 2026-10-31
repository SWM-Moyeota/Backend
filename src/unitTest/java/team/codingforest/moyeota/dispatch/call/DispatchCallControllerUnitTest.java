package team.codingforest.moyeota.dispatch.call;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.dispatch.exception.DispatchErrorCode;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.driver.domain.DriverSetting;
import team.codingforest.moyeota.driver.domain.DriverStatus;
import team.codingforest.moyeota.matching.api.dto.PartySummary;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;

import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
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

@WebMvcTest(DispatchCallController.class)
class DispatchCallControllerUnitTest extends ControllerUnitTestSupport {
    private static final Long PARTY_ID = 100L;

    @MockitoBean
    private DispatchService dispatchService;

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/accept - 성공하면 토큰의 기사 번호로 수락하고 204")
    @Test
    void accept() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/accept", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(dispatchService).acceptCall(eq(PARTY_ID), eq(DRIVER_ID));
    }

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/accept - 토큰이 없으면 401")
    @Test
    void accept_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/accept", PARTY_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UserErrorCode.UNAUTHORIZED.getCode()));
        verifyNoInteractions(dispatchService);
    }

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/accept - 토큰이 만료됐으면 401")
    @Test
    void accept_expiredToken() throws Exception {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.TOKEN_EXPIRED)).when(authService).authenticate(eq("expired-token"));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/accept", PARTY_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired-token"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(UserErrorCode.TOKEN_EXPIRED.getCode()));
        verifyNoInteractions(dispatchService);
    }

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/accept - 기사로 등록되지 않은 유저면 404")
    @Test
    void accept_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/accept", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(dispatchService);
    }

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/accept - 탈퇴한 기사면 404")
    @Test
    void accept_withdrawnDriver() throws Exception {

        /*
         * settings
         */
        Driver withdrawn = Driver.load(DRIVER_ID, USER_ID, "서울-1234-5678", null, null, null,
                DriverSetting.load(false), DriverStatus.VERIFIED, null, Instant.parse("2026-01-01T00:00:00Z"));

        doReturn(Optional.of(withdrawn)).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/accept", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(dispatchService);
    }

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/accept - 숫자가 아닌 partyId 면 400")
    @Test
    void accept_invalidPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/accept", "abc").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(dispatchService);
    }

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/accept - Long 범위를 넘는 partyId 면 400")
    @Test
    void accept_overflowPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/accept", "9223372036854775808").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(dispatchService);
    }

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/accept - 이미 마감된 콜이면 409")
    @Test
    void accept_callClosed() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DispatchErrorCode.CALL_CLOSED)).when(dispatchService).acceptCall(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/accept", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CALL_CLOSED"))
                .andExpect(jsonPath("$.message").value(DispatchErrorCode.CALL_CLOSED.getMessage()));
    }

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/accept - 이미 운행 중인 기사면 409")
    @Test
    void accept_alreadyRiding() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DispatchErrorCode.DRIVER_ALREADY_RIDING)).when(dispatchService).acceptCall(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/accept", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_ALREADY_RIDING"));
    }

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/accept - 본인이 참여한 방이면 409")
    @Test
    void accept_selfDispatch() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DispatchErrorCode.SELF_DISPATCH_NOT_ALLOWED)).when(dispatchService).acceptCall(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/accept", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_DISPATCH_NOT_ALLOWED"));
    }

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/accept - 다른 기사가 먼저 배정됐으면 409")
    @Test
    void accept_alreadyAssigned() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(MatchingErrorCode.DRIVER_ALREADY_ASSIGNED)).when(dispatchService).acceptCall(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/accept", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_ALREADY_ASSIGNED"));
    }

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/reject - 성공하면 토큰의 기사 번호로 거절하고 204")
    @Test
    void reject() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/reject", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(dispatchService).rejectCall(eq(PARTY_ID), eq(DRIVER_ID));
    }

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/reject - 토큰이 없으면 401")
    @Test
    void reject_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/reject", PARTY_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(dispatchService);
    }

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/reject - 기사로 등록되지 않은 유저면 404")
    @Test
    void reject_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/reject", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(dispatchService);
    }

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/reject - 숫자가 아닌 partyId 면 400")
    @Test
    void reject_invalidPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/reject", "abc").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(dispatchService);
    }

    @DisplayName("POST /api/v1/dispatch/calls/{partyId}/reject - 이미 마감된 콜이면 409")
    @Test
    void reject_callClosed() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DispatchErrorCode.CALL_CLOSED)).when(dispatchService).rejectCall(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/dispatch/calls/{partyId}/reject", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CALL_CLOSED"));
    }

    @DisplayName("GET /api/v1/dispatch/calls/{partyId}/status - 열린 콜이면 open true")
    @Test
    void status_open() throws Exception {

        /*
         * settings
         */
        doReturn(true).when(dispatchService).isCallOpen(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/calls/{partyId}/status", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(true));
        verify(dispatchService).isCallOpen(eq(PARTY_ID), eq(DRIVER_ID));
    }

    @DisplayName("GET /api/v1/dispatch/calls/{partyId}/status - 닫힌 콜이면 open false")
    @Test
    void status_closed() throws Exception {

        /*
         * settings
         */
        doReturn(false).when(dispatchService).isCallOpen(eq(PARTY_ID), eq(DRIVER_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/calls/{partyId}/status", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));
    }

    @DisplayName("GET /api/v1/dispatch/calls/{partyId}/status - 토큰이 없으면 401")
    @Test
    void status_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/calls/{partyId}/status", PARTY_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(dispatchService);
    }

    @DisplayName("GET /api/v1/dispatch/calls/{partyId}/status - 기사로 등록되지 않은 유저면 404")
    @Test
    void status_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/calls/{partyId}/status", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(dispatchService);
    }

    @DisplayName("GET /api/v1/dispatch/calls/{partyId}/status - 숫자가 아닌 partyId 면 400")
    @Test
    void status_invalidPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/calls/{partyId}/status", "abc").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(dispatchService);
    }

    @DisplayName("GET /api/v1/dispatch/calls/{partyId} - 방 요약 전체 필드를 돌려준다")
    @Test
    void getPartyDetail() throws Exception {

        /*
         * settings
         */
        PartySummary party = new PartySummary(PARTY_ID, 37.4979, 127.0276, 37.5665, 126.9780,
                "강남역", "서울시청", 3, 15000, 1800, null);

        doReturn(party).when(dispatchService).getDetailRoom(eq(DRIVER_ID), eq(PARTY_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/calls/{partyId}", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PARTY_ID))
                .andExpect(jsonPath("$.departureLatitude").value(37.4979))
                .andExpect(jsonPath("$.departureLongitude").value(127.0276))
                .andExpect(jsonPath("$.destinationLatitude").value(37.5665))
                .andExpect(jsonPath("$.destinationLongitude").value(126.9780))
                .andExpect(jsonPath("$.departure").value("강남역"))
                .andExpect(jsonPath("$.destination").value("서울시청"))
                .andExpect(jsonPath("$.memberCount").value(3))
                .andExpect(jsonPath("$.estimatedFare").value(15000))
                .andExpect(jsonPath("$.estimatedTime").value(1800))
                .andExpect(jsonPath("$.driverId").value(nullValue()));
        verify(dispatchService).getDetailRoom(eq(DRIVER_ID), eq(PARTY_ID));
    }

    @DisplayName("GET /api/v1/dispatch/calls/{partyId} - 예상 요금·시간이 없으면 null 로 내려준다")
    @Test
    void getPartyDetail_noEstimate() throws Exception {

        /*
         * settings
         */
        PartySummary party = new PartySummary(PARTY_ID, 37.4979, 127.0276, 37.5665, 126.9780,
                "강남역", "서울시청", 3, null, null, null);

        doReturn(party).when(dispatchService).getDetailRoom(eq(DRIVER_ID), eq(PARTY_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/calls/{partyId}", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedFare").value(nullValue()))
                .andExpect(jsonPath("$.estimatedTime").value(nullValue()));
    }

    @DisplayName("GET /api/v1/dispatch/calls/{partyId} - 토큰이 없으면 401")
    @Test
    void getPartyDetail_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/calls/{partyId}", PARTY_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(dispatchService);
    }

    @DisplayName("GET /api/v1/dispatch/calls/{partyId} - 기사로 등록되지 않은 유저면 404")
    @Test
    void getPartyDetail_driverNotRegistered() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/calls/{partyId}", PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(dispatchService);
    }

    @DisplayName("GET /api/v1/dispatch/calls/{partyId} - 숫자가 아닌 partyId 면 400")
    @Test
    void getPartyDetail_invalidPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/calls/{partyId}", "abc").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(dispatchService);
    }

    @DisplayName("GET /api/v1/dispatch/calls/{partyId} - 닫힌 콜이면 409")
    @Test
    void getPartyDetail_callClosed() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DispatchErrorCode.CALL_CLOSED)).when(dispatchService).getDetailRoom(eq(DRIVER_ID), eq(PARTY_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/calls/{partyId}", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CALL_CLOSED"));
    }

    @DisplayName("GET /api/v1/dispatch/calls/{partyId} - 방이 없으면 404")
    @Test
    void getPartyDetail_partyNotFound() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(DispatchErrorCode.PARTY_NOT_FOUND)).when(dispatchService).getDetailRoom(eq(DRIVER_ID), eq(PARTY_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/dispatch/calls/{partyId}", PARTY_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARTY_NOT_FOUND"));
    }
}
