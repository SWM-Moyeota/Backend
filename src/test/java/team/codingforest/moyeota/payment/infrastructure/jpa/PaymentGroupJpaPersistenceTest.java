package team.codingforest.moyeota.payment.infrastructure.jpa;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.payment.domain.Payment;
import team.codingforest.moyeota.payment.domain.PaymentGroup;
import team.codingforest.moyeota.payment.domain.PaymentGroupStatus;
import team.codingforest.moyeota.payment.domain.PaymentLog;
import team.codingforest.moyeota.payment.domain.PaymentStatus;
import team.codingforest.moyeota.payment.domain.PaymentType;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({PaymentGroupJpa.class, JpaAuditingConfig.class})
class PaymentGroupJpaPersistenceTest {
    private static final Long 방 = 1L;
    private static final Long 기사 = 9L;
    private static final Long 방장 = 1L;
    private static final Long 동승자 = 2L;

    private final PaymentGroupJpa groups;

    @Autowired
    PaymentGroupJpaPersistenceTest(PaymentGroupJpa groups) {
        this.groups = groups;
    }

    private PaymentGroup 저장된그룹() {
        return groups.save(PaymentGroup.open(방, 기사, 10000, List.of(방장, 동승자), 500, "모여타 택시 동승 (강남역 → 판교역)", Instant.now()));
    }

    private static Payment 분담금(PaymentGroup group, Long userId) {
        return group.getPayments().stream().filter(p -> p.getType() == PaymentType.SHARE && p.isOwnedBy(userId)).findFirst().orElseThrow();
    }

    @Test
    void 저장하면_결제마다_id_가_생기고_다시_읽어도_같다() {
        PaymentGroup saved = 저장된그룹();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPayments()).extracting(Payment::getId).doesNotContainNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        PaymentGroup reloaded = groups.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getPayments()).extracting(Payment::getAmount).containsExactly(5500, 5500);
        assertThat(reloaded.getPayments()).allSatisfy(p -> assertThat(p.getLogs()).extracting(PaymentLog::status).containsExactly(PaymentStatus.READY));
    }

    @Test
    void 상태_전이를_저장하면_로그가_덧붙고_기존_로그는_그대로다() {
        PaymentGroup saved = 저장된그룹();
        Long target = 분담금(saved, 방장).getId();
        saved.attempt(target, 7L, "moyeota-1");
        saved.markFailed(target, "PG_51", Instant.now());
        groups.save(saved);

        PaymentGroup again = groups.findById(saved.getId()).orElseThrow();
        again.retry(target, 방장, Instant.now());
        groups.save(again);

        Payment reloaded = groups.findById(saved.getId()).orElseThrow().getPayment(target);
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(reloaded.getPgPaymentId()).isNull();
        assertThat(reloaded.getPaymentMethodId()).isEqualTo(7L);
        assertThat(reloaded.getLogs()).extracting(PaymentLog::status).containsExactly(PaymentStatus.READY, PaymentStatus.FAILED, PaymentStatus.READY);
        assertThat(reloaded.getLogs()).extracting(PaymentLog::id).doesNotContainNull();
    }

    @Test
    void 대납_건은_새_결제_행으로_추가된다() {
        PaymentGroup saved = 저장된그룹();
        saved.markPaid(분담금(saved, 방장).getId(), Instant.now());
        saved.markFailed(분담금(saved, 동승자).getId(), "NO_PAYMENT_METHOD", Instant.now());
        groups.save(saved);

        PaymentGroup again = groups.findById(saved.getId()).orElseThrow();
        again.cover(방장, Instant.now());
        PaymentGroup afterCover = groups.save(again);

        assertThat(afterCover.getPayments()).hasSize(3);
        Payment cover = afterCover.getPayments().stream().filter(p -> p.getType() == PaymentType.COVER).findFirst().orElseThrow();
        assertThat(cover.getId()).isNotNull();
        assertThat(cover.getAmount()).isEqualTo(5000);
    }

    @Test
    void 잔액과_그룹_상태가_저장된다() {
        PaymentGroup saved = 저장된그룹();
        saved.markPaid(분담금(saved, 방장).getId(), Instant.now());
        saved.markPaid(분담금(saved, 동승자).getId(), Instant.now());
        groups.save(saved);

        PaymentGroup reloaded = groups.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getRemainingBalance()).isZero();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentGroupStatus.COMPLETED);
    }

    @Test
    void 결제사_식별자와_결제_id_로_그룹을_찾는다() {
        PaymentGroup saved = 저장된그룹();
        Long target = 분담금(saved, 방장).getId();
        saved.attempt(target, 7L, "moyeota-xyz");
        groups.save(saved);

        assertThat(groups.findGroupIdByPgPaymentId("moyeota-xyz")).contains(saved.getId());
        assertThat(groups.findGroupIdByPaymentId(target)).contains(saved.getId());
        assertThat(groups.findGroupIdByPgPaymentId("moyeota-none")).isEmpty();
        assertThat(groups.findByPartyId(방)).isPresent();
        assertThat(groups.findByIdForUpdate(saved.getId())).isPresent();
    }

    @Test
    void 유저별_그룹_조회는_그_유저_청구가_있는_그룹만_최신순() {
        PaymentGroup first = 저장된그룹();
        PaymentGroup second = groups.save(PaymentGroup.open(2L, 기사, 8000, List.of(방장), 0, "x", Instant.now()));

        assertThat(groups.findAllByUserId(방장)).extracting(PaymentGroup::getId).containsExactly(second.getId(), first.getId());
        assertThat(groups.findAllByUserId(동승자)).extracting(PaymentGroup::getId).containsExactly(first.getId());
        assertThat(groups.findAllByUserId(99L)).isEmpty();
    }
}
