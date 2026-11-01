package team.codingforest.moyeota.settlement.ledger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;
import team.codingforest.moyeota.settlement.ledger.domain.Payout;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutStatus;
import team.codingforest.moyeota.settlement.ledger.domain.Payouts;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedgers;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementStatus;
import team.codingforest.moyeota.settlement.ledger.domain.Settlements;
import team.codingforest.moyeota.settlement.ledger.dto.SettlementDetailResponse;
import team.codingforest.moyeota.settlement.ledger.dto.WeeklySettlementResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SettlementQueryServiceUnitTest {
    private static final Long DRIVER_ID = 9L;
    private static final Long OTHER_DRIVER_ID = 10L;
    private static final Long SETTLEMENT_ID = 100L;
    private static final SettlementPeriod WEEK = SettlementPeriod.weekOf(LocalDate.of(2026, 10, 21));

    @Mock
    private Settlements settlements;
    @Mock
    private SettlementLedgers ledgers;
    @Mock
    private Payouts payouts;

    @InjectMocks
    private SettlementQueryService queryService;

    // ---------- findWeekly ----------

    @DisplayName("findWeekly - 정산서가 있으면 정산서 금액과 상태를 주고 미정산 원장은 조회하지 않는다")
    @Test
    void findWeekly_settled() {

        /*
         * settings
         */
        Settlement settlement = settlement(DRIVER_ID, 45000, SettlementStatus.CONFIRMED);
        doReturn(Optional.of(settlement)).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(List.of(
                ledger(1L, 15000, 0, kst("2026-10-19T13:00")),
                ledger(2L, 14000, 1000, kst("2026-10-21T08:00")),
                ledger(3L, 14000, 1000, kst("2026-10-21T10:00"))
        )).when(ledgers).findBySettlementId(eq(SETTLEMENT_ID));

        /*
         * test
         */
        WeeklySettlementResponse response = queryService.findWeekly(DRIVER_ID, WEEK);

        /*
         * verify
         */
        assertThat(response.weekStart()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(response.weekEnd()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(response.settlementId()).isEqualTo(SETTLEMENT_ID);
        assertThat(response.amount()).isEqualTo(45000);
        assertThat(response.status()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(response.days()).hasSize(7);
        assertThat(response.days()).extracting(WeeklySettlementResponse.DailyEarning::date)
                .containsExactlyElementsOf(WEEK.dates());
        assertThat(response.days()).extracting(WeeklySettlementResponse.DailyEarning::amount)
                .containsExactly(15000, 0, 30000, 0, 0, 0, 0);
        assertThat(response.days()).extracting(WeeklySettlementResponse.DailyEarning::rideCount)
                .containsExactly(1, 0, 2, 0, 0, 0, 0);
        verify(ledgers, never()).findUnsettledByDriverIn(anyLong(), any());
    }

    @DisplayName("findWeekly - 정산서 상태는 그대로 내려간다")
    @ParameterizedTest
    @EnumSource(SettlementStatus.class)
    void findWeekly_settled_status(SettlementStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(settlement(DRIVER_ID, 12000, status))).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(List.of()).when(ledgers).findBySettlementId(eq(SETTLEMENT_ID));

        /*
         * test
         */
        WeeklySettlementResponse response = queryService.findWeekly(DRIVER_ID, WEEK);

        /*
         * verify
         */
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.amount()).isEqualTo(12000);
    }

    @DisplayName("findWeekly - 정산서 금액은 그대로 두고 앞선 주 운행은 요일별 칸에 넣지 않는다")
    @Test
    void findWeekly_settled_includesEarlierWeekRide() {

        /*
         * settings
         */
        doReturn(Optional.of(settlement(DRIVER_ID, 30000, SettlementStatus.PAID))).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(List.of(
                ledger(1L, 10000, 0, kst("2026-10-18T23:59:59")),
                ledger(2L, 20000, 0, kst("2026-10-19T00:00"))
        )).when(ledgers).findBySettlementId(eq(SETTLEMENT_ID));

        /*
         * test
         */
        WeeklySettlementResponse response = queryService.findWeekly(DRIVER_ID, WEEK);

        /*
         * verify
         */
        assertThat(response.amount()).isEqualTo(30000);
        assertThat(response.days()).extracting(WeeklySettlementResponse.DailyEarning::amount)
                .containsExactly(20000, 0, 0, 0, 0, 0, 0);
        assertThat(response.days()).extracting(WeeklySettlementResponse.DailyEarning::rideCount)
                .containsExactly(1, 0, 0, 0, 0, 0, 0);
    }

    @DisplayName("findWeekly - 정산서가 없으면 미정산 운행의 합을 예정 금액으로 주고 정산서 ID 와 상태는 null 이다")
    @Test
    void findWeekly_upcoming() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(List.of(
                ledger(1L, 9000, 0, kst("2026-10-20T10:00")),
                ledger(2L, 7000, 500, kst("2026-10-22T10:00"))
        )).when(ledgers).findUnsettledByDriverIn(eq(DRIVER_ID), eq(WEEK));

        /*
         * test
         */
        WeeklySettlementResponse response = queryService.findWeekly(DRIVER_ID, WEEK);

        /*
         * verify
         */
        assertThat(response.weekStart()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(response.weekEnd()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(response.settlementId()).isNull();
        assertThat(response.status()).isNull();
        assertThat(response.amount()).isEqualTo(16500);
        assertThat(response.days()).extracting(WeeklySettlementResponse.DailyEarning::amount)
                .containsExactly(0, 9000, 0, 7500, 0, 0, 0);
        assertThat(response.days()).extracting(WeeklySettlementResponse.DailyEarning::rideCount)
                .containsExactly(0, 1, 0, 1, 0, 0, 0);
        verify(ledgers, never()).findBySettlementId(anyLong());
    }

    @DisplayName("findWeekly - 운행이 없는 주는 0원에 7칸 모두 0이다")
    @Test
    void findWeekly_upcoming_empty() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(List.of()).when(ledgers).findUnsettledByDriverIn(eq(DRIVER_ID), eq(WEEK));

        /*
         * test
         */
        WeeklySettlementResponse response = queryService.findWeekly(DRIVER_ID, WEEK);

        /*
         * verify
         */
        assertThat(response.amount()).isZero();
        assertThat(response.days()).hasSize(7);
        assertThat(response.days()).extracting(WeeklySettlementResponse.DailyEarning::amount).containsOnly(0);
        assertThat(response.days()).extracting(WeeklySettlementResponse.DailyEarning::rideCount).containsOnly(0);
    }

    @DisplayName("findWeekly - 요일 칸은 KST 자정을 경계로 나눈다")
    @Test
    void findWeekly_upcoming_kstDayBoundary() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(List.of(
                ledger(1L, 1000, 0, Instant.parse("2026-10-18T15:00:00Z")),        // 월요일 00:00 KST
                ledger(2L, 2000, 0, Instant.parse("2026-10-19T14:59:59.999999Z")), // 월요일 23:59:59.999999 KST
                ledger(3L, 4000, 0, Instant.parse("2026-10-19T15:00:00Z")),        // 화요일 00:00 KST
                ledger(4L, 8000, 0, Instant.parse("2026-10-25T14:59:59.999999Z"))  // 일요일 23:59:59.999999 KST
        )).when(ledgers).findUnsettledByDriverIn(eq(DRIVER_ID), eq(WEEK));

        /*
         * test
         */
        WeeklySettlementResponse response = queryService.findWeekly(DRIVER_ID, WEEK);

        /*
         * verify
         */
        assertThat(response.amount()).isEqualTo(15000);
        assertThat(response.days()).extracting(WeeklySettlementResponse.DailyEarning::amount)
                .containsExactly(3000, 4000, 0, 0, 0, 0, 8000);
        assertThat(response.days()).extracting(WeeklySettlementResponse.DailyEarning::rideCount)
                .containsExactly(2, 1, 0, 0, 0, 0, 1);
    }

    @DisplayName("findWeekly - 예정 금액의 합이 int 최댓값이면 그대로 준다")
    @Test
    void findWeekly_upcoming_sumAtMax() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(List.of(
                ledger(1L, Integer.MAX_VALUE - 1, 0, kst("2026-10-20T10:00")),
                ledger(2L, 1, 0, kst("2026-10-20T11:00"))
        )).when(ledgers).findUnsettledByDriverIn(eq(DRIVER_ID), eq(WEEK));

        /*
         * test
         */
        WeeklySettlementResponse response = queryService.findWeekly(DRIVER_ID, WEEK);

        /*
         * verify
         */
        assertThat(response.amount()).isEqualTo(Integer.MAX_VALUE);
        assertThat(response.days().get(1).amount()).isEqualTo(Integer.MAX_VALUE);
    }

    @DisplayName("findWeekly - 예정 금액의 합이 int 를 넘으면 잘못된 금액을 주지 않고 예외가 난다")
    @Test
    void findWeekly_upcoming_sumOverflow() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(settlements).findByDriverAndPeriod(eq(DRIVER_ID), eq(WEEK));
        doReturn(List.of(
                ledger(1L, Integer.MAX_VALUE, 0, kst("2026-10-20T10:00")),
                ledger(2L, 1, 0, kst("2026-10-20T11:00"))
        )).when(ledgers).findUnsettledByDriverIn(eq(DRIVER_ID), eq(WEEK));

        /*
         * test
         */
        assertThatThrownBy(() -> queryService.findWeekly(DRIVER_ID, WEEK))

        /*
         * verify
         */
                .isInstanceOf(ArithmeticException.class);
    }

    // ---------- findDetail ----------

    @DisplayName("findDetail - 성공 시 정산서, 저장소가 준 순서의 원장, 최근 지급을 준다")
    @Test
    void findDetail() {

        /*
         * settings
         */
        Instant morning = kst("2026-10-20T09:30");
        Instant evening = kst("2026-10-21T21:00");
        Instant requestedAt = kst("2026-10-27T10:00");
        doReturn(Optional.of(settlement(DRIVER_ID, 28500, SettlementStatus.PAID))).when(settlements).findById(eq(SETTLEMENT_ID));
        doReturn(List.of(ledger(1001L, 12000, 500, morning), ledger(1002L, 15000, 1000, evening)))
                .when(ledgers).findBySettlementId(eq(SETTLEMENT_ID));
        doReturn(Optional.of(payout(PayoutStatus.SUCCEED, null, "3333123456789", requestedAt)))
                .when(payouts).findLatestBySettlementId(eq(SETTLEMENT_ID));

        /*
         * test
         */
        SettlementDetailResponse response = queryService.findDetail(DRIVER_ID, SETTLEMENT_ID);

        /*
         * verify
         */
        assertThat(response.settlementId()).isEqualTo(SETTLEMENT_ID);
        assertThat(response.date()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(response.periodStart()).isEqualTo(Instant.parse("2026-10-18T15:00:00Z"));
        assertThat(response.periodEnd()).isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
        assertThat(response.amount()).isEqualTo(28500);
        assertThat(response.status()).isEqualTo(SettlementStatus.PAID);

        assertThat(response.rides()).containsExactly(
                new SettlementDetailResponse.LedgerItem(1001L, 12000, 500, 12500, morning),
                new SettlementDetailResponse.LedgerItem(1002L, 15000, 1000, 16000, evening));

        assertThat(response.payout()).isEqualTo(new SettlementDetailResponse.PayoutItem(
                PayoutStatus.SUCCEED, 28500, BankCode.KAKAO, "*********6789", null, requestedAt));
    }

    @DisplayName("findDetail - 지급을 시도한 적이 없으면 payout 은 null 이다")
    @Test
    void findDetail_noPayout() {

        /*
         * settings
         */
        doReturn(Optional.of(settlement(DRIVER_ID, 12000, SettlementStatus.CONFIRMED))).when(settlements).findById(eq(SETTLEMENT_ID));
        doReturn(List.of(ledger(1001L, 12000, 0, kst("2026-10-20T09:30")))).when(ledgers).findBySettlementId(eq(SETTLEMENT_ID));
        doReturn(Optional.empty()).when(payouts).findLatestBySettlementId(eq(SETTLEMENT_ID));

        /*
         * test
         */
        SettlementDetailResponse response = queryService.findDetail(DRIVER_ID, SETTLEMENT_ID);

        /*
         * verify
         */
        assertThat(response.payout()).isNull();
        assertThat(response.rides()).hasSize(1);
    }

    @DisplayName("findDetail - 담긴 원장이 없으면 rides 는 빈 목록이다")
    @Test
    void findDetail_noLedger() {

        /*
         * settings
         */
        doReturn(Optional.of(settlement(DRIVER_ID, 12000, SettlementStatus.CONFIRMED))).when(settlements).findById(eq(SETTLEMENT_ID));
        doReturn(List.of()).when(ledgers).findBySettlementId(eq(SETTLEMENT_ID));
        doReturn(Optional.empty()).when(payouts).findLatestBySettlementId(eq(SETTLEMENT_ID));

        /*
         * test
         */
        SettlementDetailResponse response = queryService.findDetail(DRIVER_ID, SETTLEMENT_ID);

        /*
         * verify
         */
        assertThat(response.rides()).isEmpty();
    }

    @DisplayName("findDetail - 없는 정산서면 SETTLEMENT_NOT_FOUND 이고 원장과 지급을 조회하지 않는다")
    @Test
    void findDetail_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(settlements).findById(eq(SETTLEMENT_ID));

        /*
         * test
         */
        assertThatThrownBy(() -> queryService.findDetail(DRIVER_ID, SETTLEMENT_ID))

        /*
         * verify
         */
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        verifyNoInteractions(ledgers, payouts);
    }

    @DisplayName("findDetail - 다른 기사의 정산서면 없는 정산서와 똑같이 실패하고 원장과 지급을 조회하지 않는다")
    @Test
    void findDetail_otherDriver() {

        /*
         * settings
         */
        doReturn(Optional.of(settlement(OTHER_DRIVER_ID, 77000, SettlementStatus.PAID))).when(settlements).findById(eq(SETTLEMENT_ID));

        /*
         * test
         */
        assertThatThrownBy(() -> queryService.findDetail(DRIVER_ID, SETTLEMENT_ID))

        /*
         * verify
         */
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        verifyNoInteractions(ledgers, payouts);
    }

    @DisplayName("findDetail - 기사 ID 가 null 이면 소유자가 아니므로 실패한다")
    @Test
    void findDetail_nullDriver() {

        /*
         * settings
         */
        doReturn(Optional.of(settlement(DRIVER_ID, 12000, SettlementStatus.PAID))).when(settlements).findById(eq(SETTLEMENT_ID));

        /*
         * test
         */
        assertThatThrownBy(() -> queryService.findDetail(null, SETTLEMENT_ID))

        /*
         * verify
         */
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        verifyNoInteractions(ledgers, payouts);
    }

    @DisplayName("findDetail - 지급 실패 사유는 FAILED 일 때 그대로 내려간다")
    @Test
    void findDetail_failReason_failed() {

        /*
         * settings
         */
        stubDetailWithPayout(payout(PayoutStatus.FAILED, "예금주 불일치", "3333123456789", kst("2026-10-27T10:00")));

        /*
         * test
         */
        SettlementDetailResponse response = queryService.findDetail(DRIVER_ID, SETTLEMENT_ID);

        /*
         * verify
         */
        assertThat(response.payout().status()).isEqualTo(PayoutStatus.FAILED);
        assertThat(response.payout().failReason()).isEqualTo("예금주 불일치");
    }

    @DisplayName("findDetail - FAILED 가 아니면 저장된 사유가 있어도 failReason 은 null 이다")
    @ParameterizedTest
    @EnumSource(value = PayoutStatus.class, names = {"REQUESTED", "UNKNOWN", "SUCCEED"})
    void findDetail_failReason_hidden(PayoutStatus status) {

        /*
         * settings
         */
        stubDetailWithPayout(payout(status, "지급 요청 결과를 받지 못함", "3333123456789", kst("2026-10-27T10:00")));

        /*
         * test
         */
        SettlementDetailResponse response = queryService.findDetail(DRIVER_ID, SETTLEMENT_ID);

        /*
         * verify
         */
        assertThat(response.payout().status()).isEqualTo(status);
        assertThat(response.payout().failReason()).isNull();
    }

    @DisplayName("findDetail - 계좌번호는 끝 4자리만 보이고 4자리 이하면 전부 가린다")
    @ParameterizedTest
    @CsvSource({
            "123, ***",
            "1234, ****",
            "12345, *2345",
            "3333123456789, *********6789"
    })
    void findDetail_maskedAccountNumber(String accountNumber, String masked) {

        /*
         * settings
         */
        stubDetailWithPayout(payout(PayoutStatus.SUCCEED, null, accountNumber, kst("2026-10-27T10:00")));

        /*
         * test
         */
        SettlementDetailResponse response = queryService.findDetail(DRIVER_ID, SETTLEMENT_ID);

        /*
         * verify
         */
        assertThat(response.payout().maskedAccountNumber()).isEqualTo(masked);
    }

    private void stubDetailWithPayout(Payout payout) {
        doReturn(Optional.of(settlement(DRIVER_ID, 12000, SettlementStatus.PAID))).when(settlements).findById(eq(SETTLEMENT_ID));
        doReturn(List.of()).when(ledgers).findBySettlementId(eq(SETTLEMENT_ID));
        doReturn(Optional.of(payout)).when(payouts).findLatestBySettlementId(eq(SETTLEMENT_ID));
    }

    private static Settlement settlement(Long driverId, int amount, SettlementStatus status) {
        return Settlement.load(SETTLEMENT_ID, driverId, WEEK.start(), WEEK.end(), amount, status, null, null);
    }

    private static SettlementLedger ledger(Long matchId, int totalFare, int platformPromotion, Instant occurredAt) {
        return SettlementLedger.load(matchId, DRIVER_ID, SETTLEMENT_ID, matchId, totalFare + platformPromotion, totalFare,
                platformPromotion, occurredAt, null);
    }

    private static Payout payout(PayoutStatus status, String failReason, String accountNumber, Instant requestedAt) {
        return Payout.load(70L, SETTLEMENT_ID, 28500, status, null, BankCode.KAKAO, accountNumber, "홍길동", failReason, requestedAt, requestedAt);
    }

    private static Instant kst(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(SettlementPeriod.ZONE).toInstant();
    }
}
