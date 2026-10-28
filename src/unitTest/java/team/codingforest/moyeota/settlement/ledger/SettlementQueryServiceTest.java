package team.codingforest.moyeota.settlement.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;
import team.codingforest.moyeota.settlement.ledger.domain.Payout;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutStatus;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementPeriod;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementStatus;
import team.codingforest.moyeota.settlement.ledger.dto.SettlementDetailResponse;
import team.codingforest.moyeota.settlement.ledger.dto.SettlementDetailResponse.LedgerItem;
import team.codingforest.moyeota.settlement.ledger.dto.SettlementDetailResponse.PayoutItem;
import team.codingforest.moyeota.settlement.ledger.dto.WeeklySettlementResponse;
import team.codingforest.moyeota.settlement.ledger.dto.WeeklySettlementResponse.DailyEarning;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementQueryServiceTest {
    private static final Long 기사 = 9L;
    private static final Long 다른기사 = 10L;
    private static final LocalDate 시월_19일_월요일 = LocalDate.of(2026, 10, 19);
    private static final LocalDate 시월_21일_수요일 = LocalDate.of(2026, 10, 21);
    private static final LocalDate 시월_25일_일요일 = LocalDate.of(2026, 10, 25);
    private static final LocalDate 시월_26일_월요일 = LocalDate.of(2026, 10, 26);
    private static final LocalDate 시월_28일_수요일 = LocalDate.of(2026, 10, 28);
    private static final LocalDate 십일월_1일_일요일 = LocalDate.of(2026, 11, 1);
    private static final Instant 지금 = kst("2026-10-28T12:00");

    private FakePayouts payouts;
    private FakeSettlements settlements;
    private FakeSettlementLedgers ledgers;
    private SettlementCloseService closeService;
    private SettlementQueryService service;

    @BeforeEach
    void setUp() {
        payouts = new FakePayouts();
        settlements = new FakeSettlements(payouts);
        ledgers = new FakeSettlementLedgers(settlements);
        closeService = new SettlementCloseService(settlements, ledgers);
        service = new SettlementQueryService(settlements, ledgers, payouts);
    }

    @Test
    void 정산서가_있는_주는_정산서에_담긴_운행을_월요일부터_일요일까지_7칸에_KST_날짜별로_묶어_준다() {
        운행(기사, 1001L, 10000, 0, kst("2026-10-19T00:00"));
        운행(기사, 1002L, 15000, 1000, kst("2026-10-21T09:00"));
        운행(기사, 1003L, 12000, 500, kst("2026-10-21T21:30"));
        운행(기사, 1004L, 20000, 1000, kst("2026-10-25T23:59:59.999999"));
        Settlement 지난_주 = 정산서_생성(기사, 시월_19일_월요일);

        WeeklySettlementResponse response = service.findWeekly(기사, 시월_21일_수요일, 지금);

        assertThat(response.weekStart()).isEqualTo(시월_19일_월요일);
        assertThat(response.weekEnd()).isEqualTo(시월_25일_일요일);
        assertThat(response.settlementId()).isEqualTo(지난_주.getId());
        assertThat(response.amount()).isEqualTo(59500);
        assertThat(response.status()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(response.days()).extracting(DailyEarning::date).containsExactly(
                LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 21), LocalDate.of(2026, 10, 22),
                LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 25));
        assertThat(response.days()).extracting(DailyEarning::amount).containsExactly(10000, 0, 28500, 0, 0, 0, 21000);
        assertThat(response.days()).extracting(DailyEarning::rideCount).containsExactly(1, 0, 2, 0, 0, 0, 1);
        assertThat(response.days().stream().mapToInt(DailyEarning::amount).sum()).isEqualTo(response.amount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-19", "2026-10-21", "2026-10-25"})
    void 그_주의_어느_날짜로_조회해도_같은_주를_준다(String date) {
        운행(기사, 1001L, 12000, 0, kst("2026-10-21T09:00"));
        Settlement 지난_주 = 정산서_생성(기사, 시월_19일_월요일);

        WeeklySettlementResponse response = service.findWeekly(기사, LocalDate.parse(date), 지금);

        assertThat(response.settlementId()).isEqualTo(지난_주.getId());
        assertThat(response.weekStart()).isEqualTo(시월_19일_월요일);
        assertThat(response.weekEnd()).isEqualTo(시월_25일_일요일);
    }

    @Test
    void 이번_주는_정산서가_없어_settlementId_와_status_가_null_이고_미정산_운행으로_예정_금액을_준다() {
        운행(기사, 1001L, 8000, 0, kst("2026-10-20T10:00"));
        정산서_생성(기사, 시월_19일_월요일);
        운행(기사, 1002L, 15000, 1000, kst("2026-10-26T08:00"));
        운행(기사, 1003L, 12000, 0, kst("2026-10-28T09:00"));
        운행(다른기사, 1004L, 50000, 0, kst("2026-10-27T11:00"));

        WeeklySettlementResponse response = service.findWeekly(기사, 시월_28일_수요일, 지금);

        assertThat(response.weekStart()).isEqualTo(시월_26일_월요일);
        assertThat(response.weekEnd()).isEqualTo(십일월_1일_일요일);
        assertThat(response.settlementId()).isNull();
        assertThat(response.status()).isNull();
        assertThat(response.days()).extracting(DailyEarning::amount).containsExactly(16000, 0, 12000, 0, 0, 0, 0);
        assertThat(response.days()).extracting(DailyEarning::rideCount).containsExactly(1, 0, 1, 0, 0, 0, 0);
        assertThat(response.amount()).isEqualTo(16000 + 12000);
        assertThat(response.days().stream().mapToInt(DailyEarning::amount).sum()).isEqualTo(response.amount());
    }

    @Test
    void 월요일_마감_전_지난_주_미정산_운행은_이번_주_금액과_7칸에_들어가지_않는다() {
        운행(기사, 1001L, 15000, 0, kst("2026-10-25T22:00"));
        운행(기사, 1002L, 9000, 0, kst("2026-10-26T00:01"));

        WeeklySettlementResponse response = service.findWeekly(기사, null, kst("2026-10-26T00:02"));

        assertThat(response.weekStart()).isEqualTo(시월_26일_월요일);
        assertThat(response.amount()).isEqualTo(9000);
        assertThat(response.days()).extracting(DailyEarning::amount).containsExactly(9000, 0, 0, 0, 0, 0, 0);
        assertThat(response.days().stream().mapToInt(DailyEarning::amount).sum()).isEqualTo(response.amount());
    }

    @Test
    void 앞선_주_운행이_함께_담긴_정산서는_그_운행이_7칸에_보이지_않아_금액이_7칸_합보다_크다() {
        운행(기사, 1001L, 8000, 0, kst("2026-10-21T10:00"));
        운행(기사, 1002L, 12000, 0, kst("2026-10-27T10:00"));
        Settlement 시월_26일_주 = 정산서_생성(기사, 시월_26일_월요일);

        WeeklySettlementResponse response = service.findWeekly(기사, 시월_28일_수요일, 지금);

        assertThat(response.settlementId()).isEqualTo(시월_26일_주.getId());
        assertThat(response.amount()).isEqualTo(20000);
        assertThat(response.days()).extracting(DailyEarning::amount).containsExactly(0, 12000, 0, 0, 0, 0, 0);
    }

    @Test
    void 운행이_없는_이번_주는_settlementId_와_status_가_null_이고_금액_0_에_7칸_모두_0이다() {
        운행(기사, 1001L, 8000, 0, kst("2026-10-20T10:00"));
        정산서_생성(기사, 시월_19일_월요일);
        운행(다른기사, 1002L, 50000, 0, kst("2026-10-27T11:00"));

        WeeklySettlementResponse response = service.findWeekly(기사, 시월_28일_수요일, 지금);

        assertThat(response.settlementId()).isNull();
        assertThat(response.status()).isNull();
        assertThat(response.amount()).isZero();
        assertThat(response.days()).hasSize(7)
                .allSatisfy(day -> {
                    assertThat(day.amount()).isZero();
                    assertThat(day.rideCount()).isZero();
                });
    }

    @Test
    void 정산서가_없는_지난_주는_미정산_운행이_있어도_금액_0_에_7칸_모두_0이다() {
        운행(기사, 1001L, 8000, 0, kst("2026-10-21T10:00"));

        WeeklySettlementResponse response = service.findWeekly(기사, 시월_21일_수요일, 지금);

        assertThat(response.weekStart()).isEqualTo(시월_19일_월요일);
        assertThat(response.weekEnd()).isEqualTo(시월_25일_일요일);
        assertThat(response.settlementId()).isNull();
        assertThat(response.status()).isNull();
        assertThat(response.amount()).isZero();
        assertThat(response.days()).hasSize(7).allSatisfy(day -> assertThat(day.amount()).isZero());
        assertThat(response.days()).extracting(DailyEarning::date).startsWith(시월_19일_월요일).endsWith(시월_25일_일요일);
    }

    @Test
    void 미래_주는_이번_주_미정산_운행이_있어도_금액_0_에_7칸_모두_0이다() {
        운행(기사, 1001L, 8000, 0, kst("2026-10-27T10:00"));

        WeeklySettlementResponse response = service.findWeekly(기사, LocalDate.of(2026, 11, 4), 지금);

        assertThat(response.weekStart()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(response.settlementId()).isNull();
        assertThat(response.amount()).isZero();
        assertThat(response.days()).allSatisfy(day -> assertThat(day.rideCount()).isZero());
    }

    @Test
    void date_가_없으면_지금의_KST_날짜가_속한_주를_준다() {
        운행(기사, 1001L, 12000, 0, kst("2026-11-02T00:10"));
        Instant UTC로는_일요일 = Instant.parse("2026-11-01T15:30:00Z");

        WeeklySettlementResponse response = service.findWeekly(기사, null, UTC로는_일요일);

        assertThat(response.weekStart()).as("UTC 11-01(일) 15:30 은 KST 11-02(월) 00:30 이다").isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(response.weekEnd()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(response.days()).extracting(DailyEarning::amount).containsExactly(12000, 0, 0, 0, 0, 0, 0);
        assertThat(response.amount()).isEqualTo(12000);
    }

    @Test
    void date_가_있으면_지금과_무관하게_그_날짜가_속한_주를_준다() {
        운행(기사, 1001L, 9000, 0, kst("2026-10-27T10:00"));
        Settlement 시월_26일_주 = 정산서_생성(기사, 시월_26일_월요일);

        WeeklySettlementResponse response = service.findWeekly(기사, 십일월_1일_일요일, kst("2026-12-15T12:00"));

        assertThat(response.weekStart()).isEqualTo(시월_26일_월요일);
        assertThat(response.settlementId()).isEqualTo(시월_26일_주.getId());
        assertThat(response.amount()).isEqualTo(9000);
    }

    @Test
    void 해를_넘어가는_주는_12월_월요일부터_1월_일요일까지_7칸이다() {
        운행(기사, 1001L, 10000, 0, kst("2026-12-31T23:00"));
        운행(기사, 1002L, 15000, 0, kst("2027-01-01T01:00"));
        Settlement 연말 = 정산서_생성(기사, LocalDate.of(2026, 12, 31));

        WeeklySettlementResponse response = service.findWeekly(기사, LocalDate.of(2027, 1, 2), 지금);

        assertThat(response.weekStart()).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(response.weekEnd()).isEqualTo(LocalDate.of(2027, 1, 3));
        assertThat(response.settlementId()).isEqualTo(연말.getId());
        assertThat(response.days()).extracting(DailyEarning::amount).containsExactly(0, 0, 0, 10000, 15000, 0, 0);
    }

    @Test
    void 상세는_정산서_정보와_운행_원장을_운행_종료_시각_순으로_준다() {
        운행(기사, 2001L, 8000, 0, kst("2026-10-20T12:00"));
        정산서_생성(기사, 시월_19일_월요일);
        운행(기사, 1003L, 15000, 1000, kst("2026-11-01T21:30"));
        운행(기사, 1001L, 12000, 0, kst("2026-10-26T00:10"));
        운행(기사, 1002L, 9000, 500, kst("2026-10-28T12:00"));
        Settlement 정산서 = 정산서_생성(기사, 시월_26일_월요일);

        SettlementDetailResponse detail = service.findDetail(기사, 정산서.getId());

        assertThat(detail.settlementId()).isEqualTo(정산서.getId());
        assertThat(detail.date()).isEqualTo(시월_26일_월요일);
        assertThat(detail.periodStart()).isEqualTo(Instant.parse("2026-10-25T15:00:00Z"));
        assertThat(detail.periodEnd()).isEqualTo(Instant.parse("2026-11-01T15:00:00Z"));
        assertThat(detail.amount()).isEqualTo(37500);
        assertThat(detail.status()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(detail.rides()).extracting(LedgerItem::partyId).containsExactly(1001L, 1002L, 1003L);

        LedgerItem 마지막운행 = detail.rides().getLast();
        assertThat(마지막운행.totalFare()).isEqualTo(15000);
        assertThat(마지막운행.platformPromotion()).isEqualTo(1000);
        assertThat(마지막운행.settlementAmount()).isEqualTo(16000);
        assertThat(마지막운행.occurredAt()).isEqualTo(kst("2026-11-01T21:30"));
    }

    @Test
    void 다른_기사의_정산서_상세는_SETTLEMENT_NOT_FOUND() {
        Settlement 남의_정산서 = 정산서_저장(다른기사, 시월_28일_수요일, SettlementStatus.PAID, 12000);

        assertThatThrownBy(() -> service.findDetail(기사, 남의_정산서.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
    }

    @Test
    void 없는_정산서_상세는_SETTLEMENT_NOT_FOUND() {
        assertThatThrownBy(() -> service.findDetail(기사, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SettlementErrorCode.SETTLEMENT_NOT_FOUND);
    }

    @Test
    void 지급은_가장_최근_1건만_주고_계좌번호는_끝_4자리만_보인다() {
        Settlement 정산서 = 정산서_저장(기사, 시월_19일_월요일, SettlementStatus.PAID, 25000);
        지급(정산서, PayoutStatus.FAILED, "예금주 불일치", kst("2026-10-26T10:00"));
        지급(정산서, PayoutStatus.FAILED, "해지된 계좌", kst("2026-10-27T10:00"));
        지급(정산서, PayoutStatus.SUCCEED, null, kst("2026-10-28T10:00"));

        PayoutItem payout = service.findDetail(기사, 정산서.getId()).payout();

        assertThat(payout.status()).isEqualTo(PayoutStatus.SUCCEED);
        assertThat(payout.amount()).isEqualTo(25000);
        assertThat(payout.bankCode()).isEqualTo(FakePayoutAccounts.SAMPLE_BANK);
        assertThat(payout.maskedAccountNumber()).isEqualTo("********6789");
        assertThat(payout.failReason()).isNull();
        assertThat(payout.requestedAt()).isEqualTo(kst("2026-10-28T10:00"));
    }

    @Test
    void 실패한_지급은_실패_사유를_준다() {
        Settlement 정산서 = 정산서_저장(기사, 시월_19일_월요일, SettlementStatus.FAILED, 25000);
        지급(정산서, PayoutStatus.FAILED, "해지된 계좌", kst("2026-10-26T10:00"));

        PayoutItem payout = service.findDetail(기사, 정산서.getId()).payout();

        assertThat(payout.status()).isEqualTo(PayoutStatus.FAILED);
        assertThat(payout.failReason()).isEqualTo("해지된 계좌");
    }

    @Test
    void 결과를_확인_중인_지급은_내부_사유를_내려주지_않는다() {
        Settlement 정산서 = 정산서_저장(기사, 시월_19일_월요일, SettlementStatus.CONFIRMED, 25000);
        지급(정산서, PayoutStatus.UNKNOWN, "지급 요청 결과를 받지 못함: SocketTimeoutException", kst("2026-10-26T10:00"));

        PayoutItem payout = service.findDetail(기사, 정산서.getId()).payout();

        assertThat(payout.status()).isEqualTo(PayoutStatus.UNKNOWN);
        assertThat(payout.failReason()).isNull();
    }

    @Test
    void 지급을_시도한_적이_없으면_payout_은_null_이고_다른_정산서의_지급은_섞이지_않는다() {
        Settlement 지난_주 = 정산서_저장(기사, 시월_19일_월요일, SettlementStatus.PAID, 12000);
        지급(지난_주, PayoutStatus.SUCCEED, null, kst("2026-10-26T10:00"));
        운행(기사, 1001L, 9000, 0, kst("2026-10-27T10:00"));
        Settlement 정산서 = 정산서_생성(기사, 시월_26일_월요일);

        SettlementDetailResponse detail = service.findDetail(기사, 정산서.getId());

        assertThat(detail.payout()).isNull();
        assertThat(detail.rides()).extracting(LedgerItem::partyId).containsExactly(1001L);
    }

    private void 운행(Long taxiDriverId, Long partyId, int totalFare, int platformPromotion, Instant occurredAt) {
        ledgers.save(SettlementLedger.record(taxiDriverId, partyId, totalFare, platformPromotion, occurredAt));
    }

    private Settlement 정산서_생성(Long taxiDriverId, LocalDate date) {
        SettlementPeriod week = SettlementPeriod.weekOf(date);
        closeService.close(taxiDriverId, week);
        return settlements.findByDriverAndPeriod(taxiDriverId, week).orElseThrow();
    }

    private Settlement 정산서_저장(Long taxiDriverId, LocalDate date, SettlementStatus status, int amount) {
        SettlementPeriod period = SettlementPeriod.weekOf(date);
        return settlements.save(Settlement.load(null, taxiDriverId, period.start(), period.end(), amount, status, null, null));
    }

    private void 지급(Settlement settlement, PayoutStatus status, String failReason, Instant requestedAt) {
        payouts.setNow(requestedAt);
        String pgId = status == PayoutStatus.SUCCEED ? "pg-" + settlement.getId() : null;
        payouts.save(Payout.load(null, settlement.getId(), settlement.getAmount(), status, pgId, FakePayoutAccounts.SAMPLE_BANK,
                FakePayoutAccounts.SAMPLE_ACCOUNT_NUMBER, FakePayoutAccounts.SAMPLE_ACCOUNT_HOLDER, failReason, null, null));
    }

    private static Instant kst(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(SettlementPeriod.ZONE).toInstant();
    }
}
