package team.codingforest.moyeota.settlement.ledger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.driver.domain.Driver;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutStatus;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementStatus;
import team.codingforest.moyeota.settlement.ledger.dto.SettlementDetailResponse;
import team.codingforest.moyeota.settlement.ledger.dto.WeeklySettlementResponse;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SettlementController.class)
class SettlementControllerUnitTest extends ControllerUnitTestSupport {
    private static final String WEEKLY_URL = "/api/v1/drivers/me/settlements";
    private static final String DETAIL_URL = "/api/v1/drivers/me/settlements/{settlementId}";
    private static final Long SETTLEMENT_ID = 100L;
    private static final SettlementPeriod WEEK = SettlementPeriod.weekOf(LocalDate.of(2026, 10, 21));

    @MockitoBean
    private SettlementQueryService settlementQueryService;

    // ---------- GET /api/v1/drivers/me/settlements ----------

    @DisplayName("GET /api/v1/drivers/me/settlements - 토큰의 기사와 고른 날짜가 속한 주로 조회해 정산서를 내려준다")
    @Test
    void findWeekly_settled() throws Exception {

        /*
         * settings
         */
        doReturn(weeklyResponse(SETTLEMENT_ID, 45000, SettlementStatus.CONFIRMED))
                .when(settlementQueryService).findWeekly(eq(DRIVER_ID), eq(WEEK));

        /*
         * test
         */
        mockMvc.perform(get(WEEKLY_URL).param("date", "2026-10-21").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weekStart").value("2026-10-19"))
                .andExpect(jsonPath("$.weekEnd").value("2026-10-25"))
                .andExpect(jsonPath("$.settlementId").value(SETTLEMENT_ID))
                .andExpect(jsonPath("$.amount").value(45000))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.days", hasSize(2)))
                .andExpect(jsonPath("$.days[0].date").value("2026-10-19"))
                .andExpect(jsonPath("$.days[0].amount").value(15000))
                .andExpect(jsonPath("$.days[0].rideCount").value(1))
                .andExpect(jsonPath("$.days[1].date").value("2026-10-20"))
                .andExpect(jsonPath("$.days[1].amount").value(0))
                .andExpect(jsonPath("$.days[1].rideCount").value(0))
                .andExpect(jsonPath("$.taxiDriverId").doesNotExist());
        verify(settlementQueryService).findWeekly(eq(DRIVER_ID), eq(WEEK));
    }

    @DisplayName("GET /api/v1/drivers/me/settlements - 정산서가 없는 주는 settlementId 와 status 가 null 로 내려간다")
    @Test
    void findWeekly_upcoming() throws Exception {

        /*
         * settings
         */
        doReturn(weeklyResponse(null, 9000, null)).when(settlementQueryService).findWeekly(eq(DRIVER_ID), eq(WEEK));

        /*
         * test
         */
        mockMvc.perform(get(WEEKLY_URL).param("date", "2026-10-21").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementId").value(nullValue()))
                .andExpect(jsonPath("$.status").value(nullValue()))
                .andExpect(jsonPath("$.amount").value(9000));
    }

    @DisplayName("GET /api/v1/drivers/me/settlements - 날짜는 월요일~일요일 경계에 맞춰 그 주로 바뀐다")
    @ParameterizedTest
    @CsvSource({
            "2026-10-18, 2026-10-12",
            "2026-10-19, 2026-10-19",
            "2026-10-25, 2026-10-19",
            "2026-10-26, 2026-10-26",
            "2028-02-29, 2028-02-28",
            "2026-12-31, 2026-12-28",
            "2027-01-01, 2026-12-28"
    })
    void findWeekly_weekBoundary(String date, String monday) throws Exception {

        /*
         * settings
         */
        SettlementPeriod expected = SettlementPeriod.weekOf(LocalDate.parse(monday));
        doReturn(weeklyResponse(null, 0, null)).when(settlementQueryService).findWeekly(eq(DRIVER_ID), eq(expected));

        /*
         * test
         */
        mockMvc.perform(get(WEEKLY_URL).param("date", date).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isOk());
        verify(settlementQueryService).findWeekly(eq(DRIVER_ID), eq(expected));
    }

    @DisplayName("GET /api/v1/drivers/me/settlements - date 를 생략하면 오늘(KST)이 속한 주로 조회한다")
    @Test
    void findWeekly_noDate() throws Exception {

        /*
         * settings
         */
        doReturn(weeklyResponse(null, 0, null)).when(settlementQueryService).findWeekly(eq(DRIVER_ID), any(SettlementPeriod.class));

        /*
         * test
         */
        SettlementPeriod before = SettlementPeriod.weekOf(Instant.now());
        mockMvc.perform(get(WEEKLY_URL).with(loginDriver()))
                .andExpect(status().isOk());
        SettlementPeriod after = SettlementPeriod.weekOf(Instant.now());

        /*
         * verify
         */
        assertThat(capturedWeek()).isIn(before, after);
    }

    @DisplayName("GET /api/v1/drivers/me/settlements - date 가 비어 있거나 공백이면 오늘(KST)이 속한 주로 조회한다")
    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void findWeekly_blankDate(String date) throws Exception {

        /*
         * settings
         */
        doReturn(weeklyResponse(null, 0, null)).when(settlementQueryService).findWeekly(eq(DRIVER_ID), any(SettlementPeriod.class));

        /*
         * test
         */
        SettlementPeriod before = SettlementPeriod.weekOf(Instant.now());
        mockMvc.perform(get(WEEKLY_URL).param("date", date).with(loginDriver()))
                .andExpect(status().isOk());
        SettlementPeriod after = SettlementPeriod.weekOf(Instant.now());

        /*
         * verify
         */
        assertThat(capturedWeek()).isIn(before, after);
    }

    @DisplayName("GET /api/v1/drivers/me/settlements - date 형식이 틀리거나 없는 날짜면 400 이고 서비스를 부르지 않는다")
    @ParameterizedTest
    @ValueSource(strings = {"2026-13-01", "2026-02-30", "2026-02-29", "2026-00-10", "2026/10/28", "20261028", "28-10-2026", "2026-10", "abc", "2026-10-28T10:00:00"})
    void findWeekly_invalidDate(String date) throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(WEEKLY_URL).param("date", date).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("date: 형식이 올바르지 않습니다."));
        verifyNoInteractions(settlementQueryService);
    }

    @DisplayName("GET /api/v1/drivers/me/settlements - 토큰이 없으면 401 이고 기사와 서비스를 조회하지 않는다")
    @Test
    void findWeekly_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(WEEKLY_URL).param("date", "2026-10-21"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(settlementQueryService, drivers);
    }

    @DisplayName("GET /api/v1/drivers/me/settlements - 토큰이 없으면 date 형식이 틀려도 400 이 아니라 401 이다")
    @Test
    void findWeekly_noToken_invalidDate() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(WEEKLY_URL).param("date", "abc"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(settlementQueryService);
    }

    @DisplayName("GET /api/v1/drivers/me/settlements - Bearer 형식이 아닌 인증 헤더는 토큰 없음과 같이 401 이다")
    @Test
    void findWeekly_notBearer() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(WEEKLY_URL).header(HttpHeaders.AUTHORIZATION, "Basic abc"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(settlementQueryService, drivers);
    }

    @DisplayName("GET /api/v1/drivers/me/settlements - 깨진 토큰이면 인증 오류 코드로 401 이다")
    @Test
    void findWeekly_invalidToken() throws Exception {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.TOKEN_INVALID)).when(authService).authenticate(eq("broken"));

        /*
         * test
         */
        mockMvc.perform(get(WEEKLY_URL).header(HttpHeaders.AUTHORIZATION, "Bearer broken"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER001"));
        verifyNoInteractions(settlementQueryService, drivers);
    }

    @DisplayName("GET /api/v1/drivers/me/settlements - 토큰의 유저가 기사가 아니면 404 이고 서비스를 부르지 않는다")
    @Test
    void findWeekly_notDriver() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get(WEEKLY_URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(settlementQueryService);
    }

    @DisplayName("GET /api/v1/drivers/me/settlements - 탈퇴한 기사면 404 이고 서비스를 부르지 않는다")
    @Test
    void findWeekly_withdrawnDriver() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(WEEKLY_URL).with(loginWithdrawnDriver()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(settlementQueryService);
    }

    @DisplayName("GET /api/v1/drivers/me/settlements - 서비스의 BusinessException 은 그 상태코드와 코드로 내려간다")
    @Test
    void findWeekly_businessException() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_STATUS))
                .when(settlementQueryService).findWeekly(eq(DRIVER_ID), eq(WEEK));

        /*
         * test
         */
        mockMvc.perform(get(WEEKLY_URL).param("date", "2026-10-21").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_SETTLEMENT_STATUS"))
                .andExpect(jsonPath("$.message").value("현재 정산 상태에서 할 수 없는 처리입니다."));
    }

    @DisplayName("GET /api/v1/drivers/me/settlements - 서비스의 IllegalArgumentException 은 400 INVALID_REQUEST 로 내려간다")
    @Test
    void findWeekly_illegalArgument() throws Exception {

        /*
         * settings
         */
        doThrow(new IllegalArgumentException("잘못된 값")).when(settlementQueryService).findWeekly(eq(DRIVER_ID), eq(WEEK));

        /*
         * test
         */
        mockMvc.perform(get(WEEKLY_URL).param("date", "2026-10-21").with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("잘못된 값"));
    }

    // ---------- GET /api/v1/drivers/me/settlements/{settlementId} ----------

    @DisplayName("GET /api/v1/drivers/me/settlements/{settlementId} - 토큰의 기사와 정산서 ID 로 조회해 상세를 내려준다")
    @Test
    void findDetail() throws Exception {

        /*
         * settings
         */
        Instant occurredAt = Instant.parse("2026-10-20T00:30:00Z");
        Instant requestedAt = Instant.parse("2026-10-27T01:00:00Z");
        SettlementDetailResponse response = new SettlementDetailResponse(SETTLEMENT_ID, LocalDate.of(2026, 10, 19), WEEK.start(), WEEK.end(),
                12500, SettlementStatus.FAILED,
                List.of(new SettlementDetailResponse.LedgerItem(1001L, 12000, 500, 12500, occurredAt)),
                new SettlementDetailResponse.PayoutItem(PayoutStatus.FAILED, 12500, BankCode.KAKAO, "*********6789", "예금주 불일치", requestedAt));
        doReturn(response).when(settlementQueryService).findDetail(eq(DRIVER_ID), eq(SETTLEMENT_ID));

        /*
         * test
         */
        mockMvc.perform(get(DETAIL_URL, SETTLEMENT_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlementId").value(SETTLEMENT_ID))
                .andExpect(jsonPath("$.date").value("2026-10-19"))
                .andExpect(jsonPath("$.periodStart").value("2026-10-18T15:00:00Z"))
                .andExpect(jsonPath("$.periodEnd").value("2026-10-25T15:00:00Z"))
                .andExpect(jsonPath("$.amount").value(12500))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.rides", hasSize(1)))
                .andExpect(jsonPath("$.rides[0].partyId").value(1001))
                .andExpect(jsonPath("$.rides[0].totalFare").value(12000))
                .andExpect(jsonPath("$.rides[0].platformPromotion").value(500))
                .andExpect(jsonPath("$.rides[0].settlementAmount").value(12500))
                .andExpect(jsonPath("$.rides[0].occurredAt").value("2026-10-20T00:30:00Z"))
                .andExpect(jsonPath("$.payout.status").value("FAILED"))
                .andExpect(jsonPath("$.payout.amount").value(12500))
                .andExpect(jsonPath("$.payout.bankCode").value("KAKAO"))
                .andExpect(jsonPath("$.payout.maskedAccountNumber").value("*********6789"))
                .andExpect(jsonPath("$.payout.failReason").value("예금주 불일치"))
                .andExpect(jsonPath("$.payout.requestedAt").value("2026-10-27T01:00:00Z"))
                .andExpect(jsonPath("$.payout.accountNumber").doesNotExist())
                .andExpect(jsonPath("$.payout.accountHolder").doesNotExist())
                .andExpect(jsonPath("$.payout.pgId").doesNotExist())
                .andExpect(jsonPath("$.taxiDriverId").doesNotExist());
        verify(settlementQueryService).findDetail(eq(DRIVER_ID), eq(SETTLEMENT_ID));
    }

    @DisplayName("GET /api/v1/drivers/me/settlements/{settlementId} - 지급 시도가 없으면 payout 은 null 이고 빈 rides 도 그대로 내려간다")
    @Test
    void findDetail_noPayout() throws Exception {

        /*
         * settings
         */
        SettlementDetailResponse response = new SettlementDetailResponse(SETTLEMENT_ID, LocalDate.of(2026, 10, 19), WEEK.start(), WEEK.end(),
                12500, SettlementStatus.CONFIRMED, List.of(), null);
        doReturn(response).when(settlementQueryService).findDetail(eq(DRIVER_ID), eq(SETTLEMENT_ID));

        /*
         * test
         */
        mockMvc.perform(get(DETAIL_URL, SETTLEMENT_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payout").value(nullValue()))
                .andExpect(jsonPath("$.rides", hasSize(0)));
    }

    @DisplayName("GET /api/v1/drivers/me/settlements/{settlementId} - 없는 정산서나 다른 기사의 정산서면 404 SETTLEMENT_NOT_FOUND 다")
    @Test
    void findDetail_notFound() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND))
                .when(settlementQueryService).findDetail(eq(DRIVER_ID), eq(SETTLEMENT_ID));

        /*
         * test
         */
        mockMvc.perform(get(DETAIL_URL, SETTLEMENT_ID).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 정산서입니다."))
                .andExpect(jsonPath("$.amount").doesNotExist())
                .andExpect(jsonPath("$.rides").doesNotExist());
    }

    @DisplayName("GET /api/v1/drivers/me/settlements/{settlementId} - settlementId 가 Long 이 아니면 400 이고 서비스를 부르지 않는다")
    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "9223372036854775808"})
    void findDetail_invalidId(String settlementId) throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(DETAIL_URL, settlementId).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("settlementId: 형식이 올바르지 않습니다."));
        verifyNoInteractions(settlementQueryService);
    }

    @DisplayName("GET /api/v1/drivers/me/settlements/{settlementId} - Long 범위 안의 값은 0, 음수, 최댓값도 서비스까지 그대로 넘어간다")
    @ParameterizedTest
    @ValueSource(longs = {0L, -1L, Long.MAX_VALUE})
    void findDetail_idBoundary(long settlementId) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND))
                .when(settlementQueryService).findDetail(eq(DRIVER_ID), eq(settlementId));

        /*
         * test
         */
        mockMvc.perform(get(DETAIL_URL, settlementId).with(loginDriver()))

        /*
         * verify
         */
                .andExpect(status().isNotFound());
        verify(settlementQueryService).findDetail(eq(DRIVER_ID), eq(settlementId));
    }

    @DisplayName("GET /api/v1/drivers/me/settlements/{settlementId} - 토큰이 없으면 401 이고 서비스를 부르지 않는다")
    @Test
    void findDetail_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(DETAIL_URL, SETTLEMENT_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(settlementQueryService, drivers);
    }

    @DisplayName("GET /api/v1/drivers/me/settlements/{settlementId} - 토큰이 만료됐으면 만료 코드로 401 이다")
    @Test
    void findDetail_expiredToken() throws Exception {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.TOKEN_EXPIRED)).when(authService).authenticate(eq("expired"));

        /*
         * test
         */
        mockMvc.perform(get(DETAIL_URL, SETTLEMENT_ID).header(HttpHeaders.AUTHORIZATION, "Bearer expired"))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER002"));
        verifyNoInteractions(settlementQueryService, drivers);
    }

    @DisplayName("GET /api/v1/drivers/me/settlements/{settlementId} - 토큰의 유저가 기사가 아니면 404 이고 서비스를 부르지 않는다")
    @Test
    void findDetail_notDriver() throws Exception {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(drivers).findByUserId(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get(DETAIL_URL, SETTLEMENT_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(settlementQueryService);
    }

    @DisplayName("GET /api/v1/drivers/me/settlements/{settlementId} - 탈퇴한 기사면 404 이고 서비스를 부르지 않는다")
    @Test
    void findDetail_withdrawnDriver() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(DETAIL_URL, SETTLEMENT_ID).with(loginWithdrawnDriver()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DRIVER_NOT_REGISTERED"));
        verifyNoInteractions(settlementQueryService);
    }

    private SettlementPeriod capturedWeek() {
        ArgumentCaptor<SettlementPeriod> captor = ArgumentCaptor.forClass(SettlementPeriod.class);
        verify(settlementQueryService).findWeekly(eq(DRIVER_ID), captor.capture());
        return captor.getValue();
    }

    private RequestPostProcessor loginWithdrawnDriver() {
        Driver driver = mock(Driver.class);
        doReturn(true).when(driver).isWithdrawn();
        doReturn(Optional.of(driver)).when(drivers).findByUserId(eq(USER_ID));

        return loginUser();
    }

    private static WeeklySettlementResponse weeklyResponse(Long settlementId, int amount, SettlementStatus status) {
        return new WeeklySettlementResponse(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), settlementId, amount, status, List.of(
                new WeeklySettlementResponse.DailyEarning(LocalDate.of(2026, 10, 19), 15000, 1),
                new WeeklySettlementResponse.DailyEarning(LocalDate.of(2026, 10, 20), 0, 0)));
    }
}
