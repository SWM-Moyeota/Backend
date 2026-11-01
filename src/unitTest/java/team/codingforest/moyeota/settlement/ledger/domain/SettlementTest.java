package team.codingforest.moyeota.settlement.ledger.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE;

class SettlementTest {
    private static final Long 정산서_번호 = 1L;
    private static final Long 기사 = 9L;
    private static final Long 다른_기사 = 10L;
    private static final SettlementPeriod 기간 = SettlementPeriod.weekOf(Instant.parse("2026-10-28T03:00:00Z"));
    private static final Instant 생성 = Instant.parse("2026-11-01T15:05:01.123456Z");

    @Test
    void 새로_만든_정산서는_확정_상태이고_금액은_원장_합계를_정하기_전까지_0이다() {
        Settlement settlement = Settlement.create(기사, 기간);

        assertThat(settlement.getId()).isNull();
        assertThat(settlement.getTaxiDriverId()).isEqualTo(기사);
        assertThat(settlement.getPeriodStart()).isEqualTo(기간.start());
        assertThat(settlement.getPeriodEnd()).isEqualTo(기간.end());
        assertThat(settlement.period()).isEqualTo(기간);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(settlement.getAmount()).isZero();
        assertThat(settlement.isPayable()).isFalse();
        assertThat(settlement.getCreatedAt()).isNull();
        assertThat(settlement.getUpdatedAt()).isNull();
    }

    @Test
    void 복원한_정산서는_값을_그대로_가진다() {
        Instant 수정 = 생성.plusSeconds(60);

        Settlement settlement = Settlement.load(정산서_번호, 기사, 기간.start(), 기간.end(), 32000, SettlementStatus.HOLD, 생성, 수정);

        assertThat(settlement.getId()).isEqualTo(정산서_번호);
        assertThat(settlement.getTaxiDriverId()).isEqualTo(기사);
        assertThat(settlement.period()).isEqualTo(기간);
        assertThat(settlement.getAmount()).isEqualTo(32000);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.HOLD);
        assertThat(settlement.getCreatedAt()).isEqualTo(생성);
        assertThat(settlement.getUpdatedAt()).isEqualTo(수정);
    }

    @Test
    void 확정된_정산서의_금액을_원장_합계로_정하면_지급할_수_있다() {
        Settlement settlement = Settlement.create(기사, 기간);

        settlement.fixAmount(24600);

        assertThat(settlement.getAmount()).isEqualTo(24600);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(settlement.isPayable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 원장_합계가_0_이하면_금액을_정할_수_없고_금액은_그대로다(int ledgerTotal) {
        Settlement settlement = Settlement.create(기사, 기간);

        assertThatThrownBy(() -> settlement.fixAmount(ledgerTotal)).isInstanceOf(IllegalArgumentException.class);
        assertThat(settlement.getAmount()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = SettlementStatus.class, mode = EXCLUDE, names = "CONFIRMED")
    void 확정_상태가_아니면_금액을_정할_수_없다(SettlementStatus status) {
        Settlement settlement = 정산서(status, 15000);

        assertErrorCode(() -> settlement.fixAmount(20000), SettlementErrorCode.INVALID_SETTLEMENT_STATUS);
        assertThat(settlement.getAmount()).isEqualTo(15000);
        assertThat(settlement.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(value = SettlementStatus.class, mode = INCLUDE, names = {"CONFIRMED", "HOLD", "FAILED"})
    void 확정_보류_실패_상태이고_금액이_있으면_지급할_수_있다(SettlementStatus status) {
        assertThat(정산서(status, 1).isPayable()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = SettlementStatus.class, mode = INCLUDE, names = {"CONFIRMED", "HOLD", "FAILED"})
    void 금액이_0이면_지급할_수_없다(SettlementStatus status) {
        assertThat(정산서(status, 0).isPayable()).isFalse();
    }

    @Test
    void 지급된_정산서는_지급할_수_없다() {
        assertThat(정산서(SettlementStatus.PAID, 15000).isPayable()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = SettlementStatus.class, mode = INCLUDE, names = {"CONFIRMED", "HOLD", "FAILED"})
    void 지급_단계의_정산서는_보류할_수_있다(SettlementStatus status) {
        Settlement settlement = 정산서(status, 15000);

        settlement.hold();

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.HOLD);
        assertThat(settlement.getAmount()).isEqualTo(15000);
    }

    @Test
    void 지급된_정산서는_보류할_수_없다() {
        Settlement settlement = 정산서(SettlementStatus.PAID, 15000);

        assertErrorCode(settlement::hold, SettlementErrorCode.INVALID_SETTLEMENT_STATUS);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
    }

    @ParameterizedTest
    @EnumSource(value = SettlementStatus.class, mode = INCLUDE, names = {"CONFIRMED", "HOLD", "FAILED"})
    void 지급을_시작하면_확정_상태가_된다(SettlementStatus status) {
        Settlement settlement = 정산서(status, 15000);

        settlement.startPayout();

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
    }

    @Test
    void 지급된_정산서는_지급을_시작할_수_없다() {
        Settlement settlement = 정산서(SettlementStatus.PAID, 15000);

        assertErrorCode(settlement::startPayout, SettlementErrorCode.INVALID_SETTLEMENT_STATUS);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    void 확정된_정산서는_지급_완료될_수_있다() {
        Settlement settlement = 정산서(SettlementStatus.CONFIRMED, 15000);

        settlement.paid();

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
        assertThat(settlement.isPayable()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = SettlementStatus.class, mode = EXCLUDE, names = "CONFIRMED")
    void 확정_상태가_아니면_지급_완료될_수_없다(SettlementStatus status) {
        Settlement settlement = 정산서(status, 15000);

        assertErrorCode(settlement::paid, SettlementErrorCode.INVALID_SETTLEMENT_STATUS);
        assertThat(settlement.getStatus()).isEqualTo(status);
    }

    @Test
    void 확정된_정산서는_지급_실패될_수_있고_다시_지급할_수_있다() {
        Settlement settlement = 정산서(SettlementStatus.CONFIRMED, 15000);

        settlement.payoutFailed();

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(settlement.isPayable()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = SettlementStatus.class, mode = EXCLUDE, names = "CONFIRMED")
    void 확정_상태가_아니면_지급_실패될_수_없다(SettlementStatus status) {
        Settlement settlement = 정산서(status, 15000);

        assertErrorCode(settlement::payoutFailed, SettlementErrorCode.INVALID_SETTLEMENT_STATUS);
        assertThat(settlement.getStatus()).isEqualTo(status);
    }

    @Test
    void 보류된_정산서는_다시_지급을_시작해_지급_완료까지_갈_수_있다() {
        Settlement settlement = 정산서(SettlementStatus.CONFIRMED, 15000);

        settlement.hold();
        settlement.startPayout();
        settlement.paid();

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PAID);
    }

    @Test
    void 정산서_주인인지_확인한다() {
        Settlement settlement = 정산서(SettlementStatus.CONFIRMED, 15000);

        assertThat(settlement.isOwnedBy(기사)).isTrue();
        assertThat(settlement.isOwnedBy(다른_기사)).isFalse();
        assertThat(settlement.isOwnedBy(null)).isFalse();
    }

    private static Settlement 정산서(SettlementStatus status, int amount) {
        return Settlement.load(정산서_번호, 기사, 기간.start(), 기간.end(), amount, status, 생성, 생성);
    }

    private static void assertErrorCode(Runnable action, SettlementErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
