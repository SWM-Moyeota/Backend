package team.codingforest.moyeota.settlement.ledger.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.account.domain.BusinessType;
import team.codingforest.moyeota.settlement.account.domain.DriverPayoutAccount;
import team.codingforest.moyeota.settlement.account.domain.SellerStatus;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;
import static org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE;

class PayoutTest {
    private static final Long 정산서_번호 = 1L;
    private static final Long 지급_번호 = 42L;
    private static final Long 기사 = 9L;
    private static final BankCode 은행 = BankCode.KOOKMIN;
    private static final String 가린_계좌번호 = "********6789";
    private static final String 예금주 = "김기사";
    private static final String 셀러 = "seller_abc";
    private static final SettlementPeriod 기간 = SettlementPeriod.weekOf(Instant.parse("2026-10-28T03:00:00Z"));
    private static final Instant 생성 = Instant.parse("2026-11-02T01:00:00.123456Z");

    @ParameterizedTest
    @EnumSource(value = SettlementStatus.class, mode = INCLUDE, names = {"CONFIRMED", "HOLD", "FAILED"})
    void 지급할_수_있는_정산서로_지급을_요청하면_금액과_셀러와_가린_계좌를_복사해_요청_상태가_된다(SettlementStatus status) {
        Payout payout = Payout.request(정산서(status, 24600), 지급_계좌(SellerStatus.APPROVED));

        assertThat(payout.getId()).isNull();
        assertThat(payout.getSettlementId()).isEqualTo(정산서_번호);
        assertThat(payout.getAmount()).isEqualTo(24600);
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
        assertThat(payout.getSellerId()).isEqualTo(셀러);
        assertThat(payout.getBankCode()).isEqualTo(은행);
        assertThat(payout.getMaskedAccountNumber()).isEqualTo(가린_계좌번호);
        assertThat(payout.getAccountHolder()).isEqualTo(예금주);
        assertThat(payout.getPgId()).isNull();
        assertThat(payout.getFailReason()).isNull();
        assertThat(payout.getCreatedAt()).isNull();
        assertThat(payout.isActive()).isTrue();
        assertThat(payout.isFinished()).isFalse();
    }

    @Test
    void 부분_승인된_셀러에게도_지급을_요청할_수_있다() {
        Payout payout = Payout.request(정산서(SettlementStatus.CONFIRMED, 24600), 지급_계좌(SellerStatus.PARTIALLY_APPROVED));

        assertThat(payout.getSellerId()).isEqualTo(셀러);
    }

    @ParameterizedTest
    @EnumSource(value = SellerStatus.class, mode = INCLUDE, names = {"APPROVAL_REQUIRED", "KYC_REQUIRED"})
    void 승인되지_않은_셀러에게는_지급을_요청할_수_없다(SellerStatus status) {
        assertThatThrownBy(() -> Payout.request(정산서(SettlementStatus.CONFIRMED, 24600), 지급_계좌(status)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 셀러_등록이_끝나지_않은_계좌로는_지급을_요청할_수_없다() {
        DriverPayoutAccount 셀러_없는_계좌 = DriverPayoutAccount.load(31L, 기사, BusinessType.CORPORATE, 은행, 가린_계좌번호, 예금주,
                null, null, 생성, 생성);

        assertThatThrownBy(() -> Payout.request(정산서(SettlementStatus.CONFIRMED, 24600), 셀러_없는_계좌))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 계좌_없이는_지급을_요청할_수_없다() {
        assertThatThrownBy(() -> Payout.request(정산서(SettlementStatus.CONFIRMED, 24600), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 지급된_정산서로는_지급을_요청할_수_없다() {
        assertErrorCode(() -> Payout.request(정산서(SettlementStatus.PAID, 24600), 지급_계좌(SellerStatus.APPROVED)),
                SettlementErrorCode.INVALID_SETTLEMENT_STATUS);
    }

    @Test
    void 금액이_0인_정산서로는_지급을_요청할_수_없다() {
        assertErrorCode(() -> Payout.request(정산서(SettlementStatus.CONFIRMED, 0), 지급_계좌(SellerStatus.APPROVED)),
                SettlementErrorCode.INVALID_PAYOUT_AMOUNT);
    }

    @Test
    void 지급_키는_접두어와_지급_번호로_만든다() {
        assertThat(지급(PayoutStatus.REQUESTED).payoutKey()).isEqualTo("moyeota-payout-42");
    }

    @Test
    void 결제사에_보낼_지급_요청에는_지급_번호와_지급_키와_금액과_셀러가_담긴다() {
        PayoutOrder order = 지급(PayoutStatus.UNKNOWN).toOrder();

        assertThat(order).isEqualTo(new PayoutOrder(지급_번호, "moyeota-payout-42", 24600, 셀러));
    }

    @Test
    void 저장_전_지급으로는_지급_요청을_만들_수_없다() {
        Payout payout = Payout.request(정산서(SettlementStatus.CONFIRMED, 24600), 지급_계좌(SellerStatus.APPROVED));

        assertThatThrownBy(payout::toOrder).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 저장_전_지급에는_지급_키가_없다() {
        Payout payout = Payout.request(정산서(SettlementStatus.CONFIRMED, 24600), 지급_계좌(SellerStatus.APPROVED));

        assertThatThrownBy(payout::payoutKey).isInstanceOf(IllegalStateException.class);
    }

    // ---------- accept ----------

    @ParameterizedTest
    @EnumSource(value = PayoutStatus.class, mode = INCLUDE, names = {"REQUESTED", "UNKNOWN"})
    void 요청했거나_결과를_모르던_지급을_결제사가_접수하면_결제사_번호가_남고_모름_사유는_지워진다(PayoutStatus status) {
        Payout payout = 지급(status, null, "타임아웃");

        payout.accept("FPA_1");

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.ACCEPTED);
        assertThat(payout.getPgId()).isEqualTo("FPA_1");
        assertThat(payout.getFailReason()).isNull();
        assertThat(payout.isActive()).isTrue();
        assertThat(payout.isFinished()).isFalse();
    }

    @Test
    void 같은_결제사_번호로_다시_접수되면_그대로_둔다() {
        Payout payout = 지급(PayoutStatus.ACCEPTED, "FPA_1", null);

        payout.accept("FPA_1");

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.ACCEPTED);
        assertThat(payout.getPgId()).isEqualTo("FPA_1");
    }

    @Test
    void 접수된_지급에_다른_결제사_번호가_오면_막는다() {
        Payout payout = 지급(PayoutStatus.ACCEPTED, "FPA_1", null);

        assertErrorCode(() -> payout.accept("FPA_2"), SettlementErrorCode.INVALID_PAYOUT_STATUS);
        assertThat(payout.getPgId()).isEqualTo("FPA_1");
    }

    @ParameterizedTest
    @EnumSource(value = PayoutStatus.class, mode = INCLUDE, names = {"SUCCEED", "FAILED"})
    void 끝난_지급은_접수로_되돌릴_수_없다(PayoutStatus status) {
        Payout payout = 지급(status);

        assertErrorCode(() -> payout.accept("FPA_9"), SettlementErrorCode.INVALID_PAYOUT_STATUS);
        assertThat(payout.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void 결제사_번호_없이는_접수할_수_없다(String pgId) {
        Payout payout = 지급(PayoutStatus.REQUESTED);

        assertThatThrownBy(() -> payout.accept(pgId)).isInstanceOf(IllegalArgumentException.class);
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.REQUESTED);
    }

    // ---------- succeed / fail / markUnknown ----------

    @Test
    void 요청한_지급이_성공하면_결제사_번호가_남는다() {
        Payout payout = 지급(PayoutStatus.REQUESTED);

        payout.succeed("pg-777");

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.SUCCEED);
        assertThat(payout.getPgId()).isEqualTo("pg-777");
        assertThat(payout.getFailReason()).isNull();
        assertThat(payout.isActive()).isTrue();
        assertThat(payout.isFinished()).isTrue();
    }

    @Test
    void 접수된_지급이_성공하면_결제사_번호가_없어도_접수_때_받은_번호를_지킨다() {
        Payout payout = 지급(PayoutStatus.ACCEPTED, "FPA_1", null);

        payout.succeed(null);

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.SUCCEED);
        assertThat(payout.getPgId()).isEqualTo("FPA_1");
    }

    @Test
    void 결과를_모르던_지급이_성공하면_모름_사유는_지워진다() {
        Payout payout = 지급(PayoutStatus.REQUESTED);
        payout.markUnknown("타임아웃");

        payout.succeed("pg-777");

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.SUCCEED);
        assertThat(payout.getPgId()).isEqualTo("pg-777");
        assertThat(payout.getFailReason()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = PayoutStatus.class, mode = INCLUDE, names = {"SUCCEED", "FAILED"})
    void 끝난_지급은_다시_성공할_수_없다(PayoutStatus status) {
        Payout payout = 지급(status);

        assertErrorCode(() -> payout.succeed("pg-888"), SettlementErrorCode.INVALID_PAYOUT_STATUS);
        assertThat(payout.getStatus()).isEqualTo(status);
        assertThat(payout.getPgId()).isNotEqualTo("pg-888");
    }

    @ParameterizedTest
    @EnumSource(value = PayoutStatus.class, mode = INCLUDE, names = {"REQUESTED", "ACCEPTED", "UNKNOWN"})
    void 결과를_기다리는_지급은_실패할_수_있고_사유가_남는다(PayoutStatus status) {
        Payout payout = 지급(status);

        payout.fail("예금주 불일치");

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(payout.getFailReason()).isEqualTo("예금주 불일치");
        assertThat(payout.isActive()).isFalse();
        assertThat(payout.isFinished()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = PayoutStatus.class, mode = INCLUDE, names = {"SUCCEED", "FAILED"})
    void 끝난_지급은_실패로_바꿀_수_없다(PayoutStatus status) {
        Payout payout = 지급(status);

        assertErrorCode(() -> payout.fail("늦게 온 실패"), SettlementErrorCode.INVALID_PAYOUT_STATUS);
        assertThat(payout.getStatus()).isEqualTo(status);
    }

    @Test
    void 요청한_지급은_결과_모름으로_바꿀_수_있다() {
        Payout payout = 지급(PayoutStatus.REQUESTED);

        payout.markUnknown("지급 요청 결과를 받지 못함: SocketTimeoutException");

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.UNKNOWN);
        assertThat(payout.getFailReason()).isEqualTo("지급 요청 결과를 받지 못함: SocketTimeoutException");
        assertThat(payout.isActive()).isTrue();
        assertThat(payout.isFinished()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PayoutStatus.class, mode = EXCLUDE, names = "REQUESTED")
    void 요청_상태가_아니면_결과_모름으로_바꿀_수_없다(PayoutStatus status) {
        Payout payout = 지급(status);

        assertErrorCode(() -> payout.markUnknown("타임아웃"), SettlementErrorCode.INVALID_PAYOUT_STATUS);
        assertThat(payout.getStatus()).isEqualTo(status);
    }

    // ---------- 상태 묶음 ----------

    @ParameterizedTest
    @EnumSource(value = PayoutStatus.class, mode = INCLUDE, names = {"REQUESTED", "ACCEPTED", "UNKNOWN", "SUCCEED"})
    void 요청_접수_모름_성공은_활성_지급이다(PayoutStatus status) {
        assertThat(지급(status).isActive()).isTrue();
    }

    @Test
    void 실패한_지급은_활성_지급이_아니다() {
        assertThat(지급(PayoutStatus.FAILED).isActive()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = PayoutStatus.class, mode = INCLUDE, names = {"SUCCEED", "FAILED"})
    void 성공과_실패는_끝난_지급이다(PayoutStatus status) {
        assertThat(지급(status).isFinished()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = PayoutStatus.class, mode = INCLUDE, names = {"REQUESTED", "ACCEPTED", "UNKNOWN"})
    void 요청_접수_모름은_끝나지_않은_지급이다(PayoutStatus status) {
        assertThat(지급(status).isFinished()).isFalse();
    }

    // ---------- 사유 ----------

    @Test
    void 사유가_255자면_그대로_남는다() {
        Payout payout = 지급(PayoutStatus.REQUESTED);
        String 사유 = "가".repeat(255);

        payout.fail(사유);

        assertThat(payout.getFailReason()).isEqualTo(사유);
    }

    @Test
    void 사유가_255자를_넘으면_255자로_잘린다() {
        Payout payout = 지급(PayoutStatus.REQUESTED);

        payout.fail("가".repeat(256));

        assertThat(payout.getFailReason()).isEqualTo("가".repeat(255));
    }

    @Test
    void 결과_모름_사유도_255자로_잘린다() {
        Payout payout = 지급(PayoutStatus.REQUESTED);

        payout.markUnknown("a".repeat(1000));

        assertThat(payout.getFailReason()).hasSize(255);
    }

    @Test
    void 사유가_없으면_비어_있다() {
        Payout payout = 지급(PayoutStatus.REQUESTED);

        payout.fail(null);

        assertThat(payout.getFailReason()).isNull();
    }

    @Test
    void 복원한_지급은_값을_그대로_가진다() {
        Instant 수정 = 생성.plusSeconds(3);

        Payout payout = Payout.load(지급_번호, 정산서_번호, 24600, PayoutStatus.FAILED, "pg-1", 셀러, 은행, 가린_계좌번호, 예금주, "한도 초과", 생성, 수정);

        assertThat(payout.getId()).isEqualTo(지급_번호);
        assertThat(payout.getSettlementId()).isEqualTo(정산서_번호);
        assertThat(payout.getAmount()).isEqualTo(24600);
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(payout.getPgId()).isEqualTo("pg-1");
        assertThat(payout.getSellerId()).isEqualTo(셀러);
        assertThat(payout.getBankCode()).isEqualTo(은행);
        assertThat(payout.getMaskedAccountNumber()).isEqualTo(가린_계좌번호);
        assertThat(payout.getAccountHolder()).isEqualTo(예금주);
        assertThat(payout.getFailReason()).isEqualTo("한도 초과");
        assertThat(payout.getCreatedAt()).isEqualTo(생성);
        assertThat(payout.getUpdatedAt()).isEqualTo(수정);
    }

    private static DriverPayoutAccount 지급_계좌(SellerStatus status) {
        return DriverPayoutAccount.load(31L, 기사, BusinessType.CORPORATE, 은행, 가린_계좌번호, 예금주, 셀러, status, 생성, 생성);
    }

    private static Settlement 정산서(SettlementStatus status, int amount) {
        return Settlement.load(정산서_번호, 기사, 기간.start(), 기간.end(), amount, status, 생성, 생성);
    }

    private static Payout 지급(PayoutStatus status) {
        String pgId = status == PayoutStatus.SUCCEED || status == PayoutStatus.ACCEPTED ? "pg-1" : null;
        return 지급(status, pgId, null);
    }

    private static Payout 지급(PayoutStatus status, String pgId, String failReason) {
        return Payout.load(지급_번호, 정산서_번호, 24600, status, pgId, 셀러, 은행, 가린_계좌번호, 예금주, failReason, 생성, 생성);
    }

    private static void assertErrorCode(Runnable action, SettlementErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
