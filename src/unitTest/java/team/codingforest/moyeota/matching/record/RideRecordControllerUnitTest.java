package team.codingforest.moyeota.matching.record;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.record.dto.RideRecordResponse;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RideRecordController.class)
class RideRecordControllerUnitTest extends ControllerUnitTestSupport {
    private static final String INVALID_REQUEST = "INVALID_REQUEST";
    private static final RideRecordResponse EMPTY = new RideRecordResponse(List.of(), null, false);

    @MockitoBean
    private RideRecordService rideRecordService;

    @DisplayName("GET /api/v1/users/me/parties - 파라미터가 없으면 토큰의 사용자·커서 null·size 20 으로 조회한다")
    @Test
    void findMyRides_defaultParams() throws Exception {

        /*
         * settings
         */
        doReturn(EMPTY).when(rideRecordService).findMyRides(eq(USER_ID), isNull(), eq(20));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/users/me/parties").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rides", hasSize(0)))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()))
                .andExpect(jsonPath("$.hasNext").value(false));
        verify(rideRecordService).findMyRides(eq(USER_ID), isNull(), eq(20));
    }

    @DisplayName("GET /api/v1/users/me/parties - cursor 와 size 를 넘기고 기록·멤버·다음 커서를 돌려준다")
    @Test
    void findMyRides() throws Exception {

        /*
         * settings
         */
        RideRecordResponse.RideRecordItem item = new RideRecordResponse.RideRecordItem(30L, "강남역", "판교역",
                Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-10-01T00:30:00Z"), 15000, 7500, 2,
                List.of(new RideRecordResponse.RideRecordMember(PUBLIC_ID, "나", "me.png"),
                        new RideRecordResponse.RideRecordMember(null, null, null)));

        doReturn(new RideRecordResponse(List.of(item), 30L, true)).when(rideRecordService).findMyRides(eq(USER_ID), eq(50L), eq(10));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/users/me/parties").with(loginUser()).param("cursor", "50").param("size", "10"))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor").value(30))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.rides", hasSize(1)))
                .andExpect(jsonPath("$.rides[0].partyId").value(30))
                .andExpect(jsonPath("$.rides[0].departure").value("강남역"))
                .andExpect(jsonPath("$.rides[0].destination").value("판교역"))
                .andExpect(jsonPath("$.rides[0].pickupTime").value("2026-10-01T00:00:00Z"))
                .andExpect(jsonPath("$.rides[0].dropoffTime").value("2026-10-01T00:30:00Z"))
                .andExpect(jsonPath("$.rides[0].totalFare").value(15000))
                .andExpect(jsonPath("$.rides[0].farePerPerson").value(7500))
                .andExpect(jsonPath("$.rides[0].currentMembers").value(2))
                .andExpect(jsonPath("$.rides[0].members", hasSize(2)))
                .andExpect(jsonPath("$.rides[0].members[0].publicId").value(PUBLIC_ID.toString()))
                .andExpect(jsonPath("$.rides[0].members[0].nickname").value("나"))
                .andExpect(jsonPath("$.rides[0].members[0].imageUrl").value("me.png"))
                .andExpect(jsonPath("$.rides[0].members[1].publicId").value(nullValue()))
                .andExpect(jsonPath("$.rides[0].members[1].nickname").value(nullValue()))
                .andExpect(jsonPath("$.rides[0].members[1].imageUrl").value(nullValue()));
        verify(rideRecordService).findMyRides(eq(USER_ID), eq(50L), eq(10));
    }

    @DisplayName("GET /api/v1/users/me/parties - 1차 모드 기록은 탑승 시각·요금이 null 로 나간다")
    @Test
    void findMyRides_withoutDriver() throws Exception {

        /*
         * settings
         */
        RideRecordResponse.RideRecordItem item = new RideRecordResponse.RideRecordItem(30L, "강남역", "판교역",
                null, Instant.parse("2026-10-01T00:30:00Z"), null, null, 0, List.of());

        doReturn(new RideRecordResponse(List.of(item), null, false)).when(rideRecordService).findMyRides(eq(USER_ID), isNull(), eq(20));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/users/me/parties").with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rides[0].pickupTime").value(nullValue()))
                .andExpect(jsonPath("$.rides[0].totalFare").value(nullValue()))
                .andExpect(jsonPath("$.rides[0].farePerPerson").value(nullValue()))
                .andExpect(jsonPath("$.rides[0].dropoffTime").value("2026-10-01T00:30:00Z"));
    }

    @DisplayName("GET /api/v1/users/me/parties - cursor 와 size 가 빈 값이면 생략과 같게 처리한다")
    @Test
    void findMyRides_blankParams() throws Exception {

        /*
         * settings
         */
        doReturn(EMPTY).when(rideRecordService).findMyRides(eq(USER_ID), isNull(), eq(20));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/users/me/parties").with(loginUser()).param("cursor", "").param("size", ""))

        /*
         * verify
         */
                .andExpect(status().isOk());
        verify(rideRecordService).findMyRides(eq(USER_ID), isNull(), eq(20));
    }

    @DisplayName("GET /api/v1/users/me/parties - 토큰이 없으면 401")
    @Test
    void findMyRides_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/users/me/parties"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(rideRecordService);
    }

    @DisplayName("GET /api/v1/users/me/parties - size 가 정수가 아니면 400 이고 서비스를 부르지 않는다")
    @Test
    void findMyRides_sizeNotInteger() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/users/me/parties").with(loginUser()).param("size", "abc"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(rideRecordService);
    }

    @DisplayName("GET /api/v1/users/me/parties - cursor 가 정수가 아니면 400 이고 서비스를 부르지 않는다")
    @Test
    void findMyRides_cursorNotInteger() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/users/me/parties").with(loginUser()).param("cursor", "abc"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(rideRecordService);
    }

    @DisplayName("GET /api/v1/users/me/parties - 서비스가 페이지 크기나 커서를 거절하면 400")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = MatchingErrorCode.class, names = {"INVALID_PAGE_SIZE", "INVALID_CURSOR"})
    void findMyRides_businessException(MatchingErrorCode errorCode) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(rideRecordService).findMyRides(eq(USER_ID), eq(50L), eq(0));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/users/me/parties").with(loginUser()).param("cursor", "50").param("size", "0"))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()));
    }
}
