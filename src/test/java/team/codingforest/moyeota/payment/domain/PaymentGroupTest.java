package team.codingforest.moyeota.payment.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentGroupTest {
    private static final Long 방 = 1L;
    private static final Long 기사 = 9L;
    private static final Long 방장 = 1L;
    private static final Long 동승자 = 2L;
    private static final Long 막차 = 3L;
    private static final Long 남남 = 99L;
    private static final List<Long> 세명 = List.of(방장, 동승자, 막차);
    private static final Instant 지금 = Instant.now();

    /** 저장소를 거치지 않으므로 id 를 직접 매긴 복제본을 만든다 */
    private PaymentGroup 열린그룹(int fare, int platformCharge, List<Long> members) {
        PaymentGroup opened = PaymentGroup.open(방, 기사, fare, members, platformCharge, "모여타 택시 동승 (강남역 → 판교역)", 지금);

        List<Payment> withIds = new ArrayList<>();
        long id = 0;
        for(Payment p : opened.getPayments()) {
            withIds.add(Payment.restore(++id, p.getUserId(), p.getAmount(), p.getType(), p.getCurrency(), p.getOrderName(), p.getCreatedAt(),
                    null, null, p.getStatus(), null, null, null, p.getLogs()));
        }
        return PaymentGroup.restore(10L, 방, 기사, fare, members.size(), platformCharge, 지금, opened.getRemainingBalance(), opened.getStatus(), withIds);
    }

    private static Payment 분담금(PaymentGroup group, Long userId) {
        return group.getPayments().stream().filter(p -> p.getType() == PaymentType.SHARE && p.isOwnedBy(userId)).findFirst().orElseThrow();
    }

    // ───────────────────────── 생성 ─────────────────────────

    @Test
    void 열면_요금이_인원수로_나뉘고_플랫폼_이용료가_1인분씩_얹힌다() {
        PaymentGroup group = 열린그룹(10000, 500, 세명);

        assertThat(group.getStatus()).isEqualTo(PaymentGroupStatus.IN_PROGRESS);
        assertThat(group.getRemainingBalance()).isEqualTo(10000);
        assertThat(group.getPassengerCount()).isEqualTo(3);
        assertThat(group.getPayments()).extracting(Payment::getAmount).containsExactly(3834, 3833, 3833);
        assertThat(group.getPayments()).allSatisfy(p -> {
            assertThat(p.getStatus()).isEqualTo(PaymentStatus.READY);
            assertThat(p.getType()).isEqualTo(PaymentType.SHARE);
            assertThat(p.getCurrency()).isEqualTo(Currency.KRW);
            assertThat(p.getLogs()).hasSize(1);
        });
    }

    @Test
    void 플랫폼_이용료가_0_이면_요금만_청구한다() {
        PaymentGroup group = 열린그룹(9000, 0, 세명);

        assertThat(group.getPayments()).extracting(Payment::getAmount).containsExactly(3000, 3000, 3000);
    }

    // ───────────────────────── 결제 결과 반영 ─────────────────────────

    @Test
    void 전원_결제되면_잔액_0_에_COMPLETED() {
        PaymentGroup group = 열린그룹(10000, 500, 세명);

        for(Payment p : group.getPayments()) group.markPaid(p.getId(), 지금);

        // 이용료는 잔액에 들어가지 않는다 - 요금 몫만 깎여 정확히 0
        assertThat(group.getRemainingBalance()).isZero();
        assertThat(group.getStatus()).isEqualTo(PaymentGroupStatus.COMPLETED);
    }

    @Test
    void 일부_실패하고_나머지_청구가_끝나면_PARTIALLY_FAILED() {
        PaymentGroup group = 열린그룹(10000, 0, 세명);

        group.markPaid(분담금(group, 방장).getId(), 지금);
        group.markFailed(분담금(group, 동승자).getId(), "PG_51", 지금);
        assertThat(group.getStatus()).as("아직 막차 청구가 안 끝남").isEqualTo(PaymentGroupStatus.IN_PROGRESS);

        group.markPaid(분담금(group, 막차).getId(), 지금);

        assertThat(group.getStatus()).isEqualTo(PaymentGroupStatus.PARTIALLY_FAILED);
        assertThat(group.getRemainingBalance()).isEqualTo(3333);
        assertThat(분담금(group, 동승자).getFailCode()).isEqualTo("PG_51");
    }

    @Test
    void 청구_준비는_READY_일_때만_되고_수단과_식별자를_남긴다() {
        PaymentGroup group = 열린그룹(10000, 0, 세명);
        Payment target = 분담금(group, 방장);

        group.attempt(target.getId(), 77L, "moyeota-abc");
        assertThat(target.getPaymentMethodId()).isEqualTo(77L);
        assertThat(target.getPgPaymentId()).isEqualTo("moyeota-abc");

        group.markPaid(target.getId(), 지금);

        assertThatThrownBy(() -> group.attempt(target.getId(), 77L, "moyeota-def"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_NOT_READY);
    }

    @Test
    void 이미_결론난_결제에_결과를_또_반영할_수_없다() {
        // 응답과 웹훅이 둘 다 도착하는 경우 - 두 번째는 도메인이 막는다
        PaymentGroup group = 열린그룹(10000, 0, 세명);
        Long id = 분담금(group, 방장).getId();
        group.markPaid(id, 지금);

        assertThatThrownBy(() -> group.markPaid(id, 지금))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_NOT_READY);
        assertThat(group.getRemainingBalance()).as("잔액이 두 번 깎이면 안 된다").isEqualTo(10000 - 3334);
    }

    // ───────────────────────── 재시도 ─────────────────────────

    @Test
    void 실패한_분담금은_본인이_재시도하면_다시_READY_가_된다() {
        PaymentGroup group = 열린그룹(10000, 0, 세명);
        Payment target = 분담금(group, 동승자);
        group.attempt(target.getId(), 1L, "moyeota-1");
        group.markFailed(target.getId(), "PG_51", 지금);

        group.retry(target.getId(), 동승자, 지금);

        assertThat(target.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(target.getFailCode()).isNull();
        assertThat(target.getPgPaymentId()).as("새 시도는 새 식별자로").isNull();
        assertThat(group.getStatus()).isEqualTo(PaymentGroupStatus.IN_PROGRESS);
    }

    @Test
    void 남의_분담금은_재시도할_수_없다() {
        PaymentGroup group = 열린그룹(10000, 0, 세명);
        Long id = 분담금(group, 동승자).getId();
        group.markFailed(id, "PG_51", 지금);

        assertThatThrownBy(() -> group.retry(id, 방장, 지금))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.NOT_PAYMENT_OWNER);
    }

    @Test
    void 성공한_결제는_재시도할_수_없다() {
        PaymentGroup group = 열린그룹(10000, 0, 세명);
        Long id = 분담금(group, 방장).getId();
        group.markPaid(id, 지금);

        assertThatThrownBy(() -> group.retry(id, 방장, 지금))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_NOT_RETRYABLE);
    }

    // ───────────────────────── 대납 ─────────────────────────

    private PaymentGroup 동승자만_실패한_그룹() {
        PaymentGroup group = 열린그룹(10000, 500, 세명);
        group.markPaid(분담금(group, 방장).getId(), 지금);
        group.markFailed(분담금(group, 동승자).getId(), "PG_51", 지금);
        group.markPaid(분담금(group, 막차).getId(), 지금);
        return group;
    }

    @Test
    void 대납_금액은_미수금_전액이고_이용료는_붙지_않는다() {
        PaymentGroup group = 동승자만_실패한_그룹();

        Payment cover = group.cover(방장, 지금);

        assertThat(cover.getType()).isEqualTo(PaymentType.COVER);
        assertThat(cover.getAmount()).isEqualTo(3333);
        assertThat(cover.isOwnedBy(방장)).isTrue();
        assertThat(group.getStatus()).as("대납 청구가 READY 라 다시 진행 중").isEqualTo(PaymentGroupStatus.IN_PROGRESS);
    }

    @Test
    void 대납이_성공하면_실패분은_COVERED_로_종결되고_그룹은_COMPLETED() {
        PaymentGroup group = 동승자만_실패한_그룹();
        Payment cover = group.cover(방장, 지금);
        Payment coverWithId = Payment.restore(100L, cover.getUserId(), cover.getAmount(), cover.getType(), cover.getCurrency(), cover.getOrderName(), cover.getCreatedAt(),
                null, null, cover.getStatus(), null, null, null, cover.getLogs());
        PaymentGroup reloaded = PaymentGroup.restore(group.getId(), 방, 기사, 10000, 3, 500, 지금, group.getRemainingBalance(), group.getStatus(),
                List.of(분담금(group, 방장), 분담금(group, 동승자), 분담금(group, 막차), coverWithId));

        reloaded.markPaid(100L, 지금);

        assertThat(reloaded.getRemainingBalance()).isZero();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentGroupStatus.COMPLETED);
        assertThat(분담금(reloaded, 동승자).getStatus()).isEqualTo(PaymentStatus.COVERED);
    }

    @Test
    void 청구가_진행_중이면_대납할_수_없다() {
        PaymentGroup group = 열린그룹(10000, 0, 세명);
        group.markFailed(분담금(group, 동승자).getId(), "PG_51", 지금);   // 나머지 둘은 아직 READY

        assertThatThrownBy(() -> group.cover(방장, 지금))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.GROUP_NOT_COVERABLE);
    }

    @Test
    void 구성원이_아니면_대납할_수_없다() {
        PaymentGroup group = 동승자만_실패한_그룹();

        assertThatThrownBy(() -> group.cover(남남, 지금))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.NOT_GROUP_MEMBER);
    }

    @Test
    void 대납_진행_중엔_재시도도_두_번째_대납도_막힌다() {
        // 대납과 재시도가 동시에 성공하면 이중 수금
        PaymentGroup group = 동승자만_실패한_그룹();
        group.cover(방장, 지금);

        assertThatThrownBy(() -> group.retry(분담금(group, 동승자).getId(), 동승자, 지금))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.COVER_ALREADY_IN_PROGRESS);
        assertThatThrownBy(() -> group.cover(막차, 지금))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.COVER_ALREADY_IN_PROGRESS);
    }

    // ───────────────────────── 취소 ─────────────────────────

    @Test
    void 결제가_취소되면_요금_몫만큼_잔액이_되살아난다() {
        PaymentGroup group = 열린그룹(10000, 500, 세명);
        for(Payment p : group.getPayments()) group.markPaid(p.getId(), 지금);
        Payment target = 분담금(group, 방장);

        group.cancel(target.getId(), "고객 요청", 지금);

        assertThat(target.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(target.getCancelReason()).isEqualTo("고객 요청");
        assertThat(group.getRemainingBalance()).isEqualTo(3334);
        assertThat(group.getStatus()).isEqualTo(PaymentGroupStatus.PARTIALLY_FAILED);
    }

    @Test
    void 결제되지_않은_건은_취소할_수_없다() {
        PaymentGroup group = 열린그룹(10000, 0, 세명);

        assertThatThrownBy(() -> group.cancel(분담금(group, 방장).getId(), "x", 지금))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.PAYMENT_NOT_PAID);
    }

    // ───────────────────────── 로그 ─────────────────────────

    @Test
    void 상태가_바뀔_때마다_로그가_쌓인다() {
        PaymentGroup group = 열린그룹(10000, 0, 세명);
        Payment target = 분담금(group, 동승자);

        group.markFailed(target.getId(), "PG_51", 지금);
        group.retry(target.getId(), 동승자, 지금);
        group.markPaid(target.getId(), 지금);

        assertThat(target.getLogs()).extracting(PaymentLog::status)
                .containsExactly(PaymentStatus.READY, PaymentStatus.FAILED, PaymentStatus.READY, PaymentStatus.PAID);
        assertThat(target.getLogs().get(1).failCode()).isEqualTo("PG_51");
    }
}
