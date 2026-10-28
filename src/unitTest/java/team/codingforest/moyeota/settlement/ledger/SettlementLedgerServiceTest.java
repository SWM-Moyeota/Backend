package team.codingforest.moyeota.settlement.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.api.dto.RideSettlementCommand;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementLedgerServiceTest {
    private static final Long 기사 = 9L;
    private static final Long 다른_기사 = 10L;
    private static final Long 운행_1 = 101L;
    private static final Long 운행_2 = 102L;

    private static final Instant 시월_22일_목요일_정오 = Instant.parse("2026-10-22T03:00:00Z");
    private static final Instant 시월_28일_수요일_정오 = Instant.parse("2026-10-28T03:00:00Z");

    private FakeSettlements settlements;
    private FakeSettlementLedgers ledgers;
    private SettlementLedgerService service;

    @BeforeEach
    void setUp() {
        settlements = new FakeSettlements(new FakePayouts());
        ledgers = new FakeSettlementLedgers(settlements);
        service = new SettlementLedgerService(ledgers);
    }

    @Test
    void 운행이_끝나면_기사의_미정산_원장_한_줄만_남기고_정산서는_만들지_않는다() {
        service.record(운행(기사, 운행_1, 15000, 2000, 시월_28일_수요일_정오));

        SettlementLedger ledger = ledgers.findByMatchId(운행_1).orElseThrow();
        assertThat(ledgers.count()).isEqualTo(1);
        assertThat(ledger.getTaxiDriverId()).isEqualTo(기사);
        assertThat(ledger.getSettlementId()).isNull();
        assertThat(ledger.getTotalFare()).isEqualTo(15000);
        assertThat(ledger.getPlatformPromotion()).isEqualTo(2000);
        assertThat(ledger.getSettlementAmount()).isEqualTo(17000);
        assertThat(ledger.getOccurredAt()).isEqualTo(시월_28일_수요일_정오);
        assertThat(ledgers.findUnsettledByDriver(기사)).extracting(SettlementLedger::getMatchId).containsExactly(운행_1);
        assertThat(settlements.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"15000, 0, 15000", "15000, 2000, 17000", "1, 0, 1", "8800, 1200, 10000"})
    void 원장의_정산_금액은_운행_금액과_프로모션의_합이다(int totalFare, int platformPromotion, int expected) {
        service.record(운행(기사, 운행_1, totalFare, platformPromotion, 시월_28일_수요일_정오));

        assertThat(ledgers.findByMatchId(운행_1).orElseThrow().getSettlementAmount()).isEqualTo(expected);
    }

    @Test
    void 기사가_다르면_각자의_미정산_원장으로_남는다() {
        service.record(운행(기사, 운행_1, 10000, 0, 시월_28일_수요일_정오));
        service.record(운행(다른_기사, 운행_2, 20000, 0, 시월_28일_수요일_정오));

        assertThat(ledgers.findUnsettledByDriver(기사)).extracting(SettlementLedger::getMatchId).containsExactly(운행_1);
        assertThat(ledgers.findUnsettledByDriver(다른_기사)).extracting(SettlementLedger::getMatchId).containsExactly(운행_2);
    }

    @Test
    void 같은_운행이_두_번_오면_한_번만_기록하고_예외도_내지_않는다() {
        service.record(운행(기사, 운행_1, 15000, 2000, 시월_28일_수요일_정오));

        assertThatCode(() -> service.record(운행(기사, 운행_1, 15000, 2000, 시월_28일_수요일_정오)))
                .doesNotThrowAnyException();

        assertThat(ledgers.count()).isEqualTo(1);
    }

    @Test
    void 이미_기록된_운행은_값이_달라도_처음_기록_그대로_둔다() {
        service.record(운행(기사, 운행_1, 15000, 2000, 시월_28일_수요일_정오));

        service.record(운행(다른_기사, 운행_1, 99000, 0, 시월_22일_목요일_정오));

        SettlementLedger ledger = ledgers.findByMatchId(운행_1).orElseThrow();
        assertThat(ledgers.count()).isEqualTo(1);
        assertThat(ledger.getTaxiDriverId()).isEqualTo(기사);
        assertThat(ledger.getTotalFare()).isEqualTo(15000);
        assertThat(ledger.getOccurredAt()).isEqualTo(시월_28일_수요일_정오);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -15000})
    void 운행_금액이_0_이하면_INVALID_TOTAL_FARE_이고_아무것도_저장하지_않는다(int totalFare) {
        assertErrorCode(운행(기사, 운행_1, totalFare, 0, 시월_28일_수요일_정오), SettlementErrorCode.INVALID_TOTAL_FARE);
        assertThat(ledgers.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -2000})
    void 프로모션이_음수면_INVALID_PLATFORM_PROMOTION_이고_아무것도_저장하지_않는다(int platformPromotion) {
        assertErrorCode(운행(기사, 운행_1, 15000, platformPromotion, 시월_28일_수요일_정오), SettlementErrorCode.INVALID_PLATFORM_PROMOTION);
        assertThat(ledgers.count()).isZero();
    }

    static Stream<RideSettlementCommand> 빈_값이_있는_요청() {
        return Stream.of(
                운행(기사, null, 15000, 0, 시월_28일_수요일_정오),
                운행(null, 운행_1, 15000, 0, 시월_28일_수요일_정오),
                운행(기사, 운행_1, 15000, 0, null));
    }

    @ParameterizedTest
    @MethodSource("빈_값이_있는_요청")
    void 운행_기사_종료_시각_중_하나라도_비면_INVALID_SETTLEMENT_COMMAND_이고_아무것도_저장하지_않는다(RideSettlementCommand command) {
        assertErrorCode(command, SettlementErrorCode.INVALID_SETTLEMENT_COMMAND);
        assertThat(ledgers.count()).isZero();
    }

    @Test
    void 요청이_null_이면_INVALID_SETTLEMENT_COMMAND_이고_아무것도_저장하지_않는다() {
        assertErrorCode(null, SettlementErrorCode.INVALID_SETTLEMENT_COMMAND);
        assertThat(ledgers.count()).isZero();
    }

    @Test
    void 검증에_실패하면_이미_쌓인_원장도_그대로다() {
        service.record(운행(기사, 운행_1, 15000, 0, 시월_28일_수요일_정오));

        assertErrorCode(운행(기사, 운행_2, 0, 0, 시월_28일_수요일_정오), SettlementErrorCode.INVALID_TOTAL_FARE);

        assertThat(ledgers.count()).isEqualTo(1);
        assertThat(ledgers.findByMatchId(운행_1).orElseThrow().getSettlementAmount()).isEqualTo(15000);
    }

    private static RideSettlementCommand 운행(Long taxiDriverId, Long partyId, int totalFare, int platformPromotion, Instant occurredAt) {
        return new RideSettlementCommand(partyId, taxiDriverId, totalFare, platformPromotion, occurredAt);
    }

    private void assertErrorCode(RideSettlementCommand command, SettlementErrorCode errorCode) {
        assertThatThrownBy(() -> service.record(command))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
