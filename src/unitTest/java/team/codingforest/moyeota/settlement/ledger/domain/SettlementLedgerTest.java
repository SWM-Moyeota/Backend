package team.codingforest.moyeota.settlement.ledger.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettlementLedgerTest {
    private static final Long 기사 = 9L;
    private static final Long 운행 = 100L;
    private static final Long 정산서 = 1L;
    private static final Instant 하차 = Instant.parse("2026-10-28T13:42:11.654321Z");

    @Test
    void 정산_금액은_운행_금액과_프로모션_금액의_합이다() {
        SettlementLedger ledger = SettlementLedger.record(기사, 운행, 15000, 2000, 하차);

        assertThat(ledger.getSettlementAmount()).isEqualTo(17000);
        assertThat(ledger.getTotalFare()).isEqualTo(15000);
        assertThat(ledger.getPlatformPromotion()).isEqualTo(2000);
        assertThat(ledger.getTaxiDriverId()).isEqualTo(기사);
        assertThat(ledger.getMatchId()).isEqualTo(운행);
        assertThat(ledger.getOccurredAt()).isEqualTo(하차);
    }

    @Test
    void 프로모션이_없으면_정산_금액은_운행_금액과_같다() {
        SettlementLedger ledger = SettlementLedger.record(기사, 운행, 9600, 0, 하차);

        assertThat(ledger.getSettlementAmount()).isEqualTo(9600);
        assertThat(ledger.getPlatformPromotion()).isZero();
    }

    @Test
    void 운행_금액은_1원부터_기록할_수_있다() {
        SettlementLedger ledger = SettlementLedger.record(기사, 운행, 1, 0, 하차);

        assertThat(ledger.getSettlementAmount()).isEqualTo(1);
    }

    @Test
    void 새로_기록한_원장은_아직_정산되지_않았고_아이디가_없다() {
        SettlementLedger ledger = SettlementLedger.record(기사, 운행, 15000, 0, 하차);

        assertThat(ledger.getId()).isNull();
        assertThat(ledger.getSettlementId()).isNull();
        assertThat(ledger.getCreatedAt()).isNull();
    }

    @Test
    void 운행_시각은_마이크로초_단위로_잘린다() {
        SettlementLedger ledger = SettlementLedger.record(기사, 운행, 15000, 0, Instant.parse("2026-11-01T14:59:59.999999999Z"));

        assertThat(ledger.getOccurredAt()).isEqualTo(Instant.parse("2026-11-01T14:59:59.999999Z"));
        assertThat(SettlementPeriod.weekOf(ledger.getOccurredAt())).isEqualTo(SettlementPeriod.weekOf(Instant.parse("2026-11-01T14:59:59.999999999Z")));
    }

    @Test
    void 기사가_없으면_기록할_수_없다() {
        assertErrorCode(() -> SettlementLedger.record(null, 운행, 15000, 0, 하차), SettlementErrorCode.INVALID_SETTLEMENT_COMMAND);
    }

    @Test
    void 운행이_없으면_기록할_수_없다() {
        assertErrorCode(() -> SettlementLedger.record(기사, null, 15000, 0, 하차), SettlementErrorCode.INVALID_SETTLEMENT_COMMAND);
    }

    @Test
    void 운행_시각이_없으면_기록할_수_없다() {
        assertErrorCode(() -> SettlementLedger.record(기사, 운행, 15000, 0, null), SettlementErrorCode.INVALID_SETTLEMENT_COMMAND);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void 운행_금액이_0_이하면_기록할_수_없다(int totalFare) {
        assertErrorCode(() -> SettlementLedger.record(기사, 운행, totalFare, 0, 하차), SettlementErrorCode.INVALID_TOTAL_FARE);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    void 프로모션_금액이_음수면_기록할_수_없다(int platformPromotion) {
        assertErrorCode(() -> SettlementLedger.record(기사, 운행, 15000, platformPromotion, 하차), SettlementErrorCode.INVALID_PLATFORM_PROMOTION);
    }

    @Test
    void 정산_금액이_int_범위를_넘으면_기록할_수_없다() {
        assertThatThrownBy(() -> SettlementLedger.record(기사, 운행, Integer.MAX_VALUE, 1, 하차))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void 복원한_원장은_값을_그대로_가진다() {
        Instant 생성 = Instant.parse("2026-10-28T13:42:12.000001Z");

        SettlementLedger ledger = SettlementLedger.load(7L, 기사, 정산서, 운행, 17000, 15000, 2000, 하차, 생성);

        assertThat(ledger.getId()).isEqualTo(7L);
        assertThat(ledger.getTaxiDriverId()).isEqualTo(기사);
        assertThat(ledger.getSettlementId()).isEqualTo(정산서);
        assertThat(ledger.getMatchId()).isEqualTo(운행);
        assertThat(ledger.getSettlementAmount()).isEqualTo(17000);
        assertThat(ledger.getTotalFare()).isEqualTo(15000);
        assertThat(ledger.getPlatformPromotion()).isEqualTo(2000);
        assertThat(ledger.getOccurredAt()).isEqualTo(하차);
        assertThat(ledger.getCreatedAt()).isEqualTo(생성);
    }

    private static void assertErrorCode(Runnable action, SettlementErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
