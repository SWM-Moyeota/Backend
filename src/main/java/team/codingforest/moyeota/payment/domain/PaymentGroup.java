package team.codingforest.moyeota.payment.domain;

import lombok.Getter;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 *  애그리거트 루트 - 운행 1건의 분할 결제 묶음.
 *  fare(미터기 요금)를 인원수로 나눈 분담금(SHARE)을 멤버별로 청구하고, remainingBalance(미수금 요금)가 0 이 되면 COMPLETED.
 *  플랫폼 이용료(platformCharge)는 분담금마다 1인분씩 얹히며 미수금 계산에는 들어가지 않는다.
 */
@Getter
public class PaymentGroup {
    private final Long id;
    private final Long partyId;
    private final Long driverId;
    private final int fare;
    private final int passengerCount;
    private final int platformCharge;
    private final Instant createdAt;
    private int remainingBalance;
    private PaymentGroupStatus status;
    private final List<Payment> payments;

    private PaymentGroup(Long id, Long partyId, Long driverId, int fare, int passengerCount, int platformCharge, Instant createdAt,
                         int remainingBalance, PaymentGroupStatus status, List<Payment> payments) {
        this.id = id;
        this.partyId = partyId;
        this.driverId = driverId;
        this.fare = fare;
        this.passengerCount = passengerCount;
        this.platformCharge = platformCharge;
        this.createdAt = createdAt;
        this.remainingBalance = remainingBalance;
        this.status = status;
        this.payments = new ArrayList<>(payments);
    }

    /**
     *  운행 종료 → 멤버별 분담금 청구 생성
     */
    public static PaymentGroup open(Long partyId, Long driverId, int fare, List<Long> memberIds, int platformCharge, String orderName, Instant now) {
        if(platformCharge < 0) throw new BusinessException(PaymentErrorCode.INVALID_AMOUNT);

        List<Integer> shares = FareSplit.evenly(fare, memberIds.size());

        PaymentGroup group = new PaymentGroup(null, partyId, driverId, fare, memberIds.size(), platformCharge, now, fare, PaymentGroupStatus.IN_PROGRESS, List.of());

        for(int i = 0; i < memberIds.size(); i++) {
            group.payments.add(Payment.ready(memberIds.get(i), shares.get(i) + platformCharge, PaymentType.SHARE, orderName, now));
        }

        return group;
    }

    /**
     *  청구 직전 - 결제수단·결제사 식별자 기록
     */
    public void attempt(Long paymentId, Long paymentMethodId, String pgPaymentId) {
        getPayment(paymentId).attempt(paymentMethodId, pgPaymentId);
    }

    public void markPaid(Long paymentId, Instant now) {
        Payment payment = getPayment(paymentId);

        payment.markPaid(now);
        remainingBalance -= payment.farePortion(platformCharge);

        if(remainingBalance <= 0) {
            remainingBalance = 0;
            // 대납으로 채워졌으면 실패해 있던 분담금들은 종결 처리
            for(Payment p : payments) {
                if(p.isFailed()) p.markCovered(now);
            }
        }

        refreshStatus();
    }

    public void markFailed(Long paymentId, String failCode, Instant now) {
        getPayment(paymentId).markFailed(failCode, now);
        refreshStatus();
    }

    /**
     *  실패한 분담금을 본인이 다시 청구
     */
    public void retry(Long paymentId, Long requesterId, Instant now) {
        Payment payment = getPayment(paymentId);
        if(!payment.isOwnedBy(requesterId)) throw new BusinessException(PaymentErrorCode.NOT_PAYMENT_OWNER);
        ensureNoCoverInProgress();   // 대납이 진행 중인데 분담금까지 다시 받으면 이중 수금

        payment.retry(now);
        refreshStatus();
    }

    /**
     *  미수금 전액을 멤버 한 명이 대납 - 모든 청구가 끝나고 실패분이 남았을 때만
     */
    public Payment cover(Long payerId, Instant now) {
        if(!hasMember(payerId)) throw new BusinessException(PaymentErrorCode.NOT_GROUP_MEMBER);
        ensureNoCoverInProgress();   // 더 구체적인 사유를 먼저 - 대납 중이면 그룹도 IN_PROGRESS 라 아래 검사에 같이 걸린다
        if(status != PaymentGroupStatus.PARTIALLY_FAILED) throw new BusinessException(PaymentErrorCode.GROUP_NOT_COVERABLE);

        Payment cover = Payment.ready(payerId, remainingBalance, PaymentType.COVER, firstOrderName(), now);
        payments.add(cover);
        refreshStatus();

        return cover;
    }

    /**
     *  결제 취소(환불) 반영 - 요금 몫만큼 미수금이 되살아난다
     */
    public void cancel(Long paymentId, String reason, Instant canceledAt) {
        Payment payment = getPayment(paymentId);

        payment.cancel(reason, canceledAt);
        remainingBalance += payment.farePortion(platformCharge);
        refreshStatus();
    }

    public boolean hasMember(Long userId) {
        for(Payment p : payments) {
            if(p.getType() == PaymentType.SHARE && p.isOwnedBy(userId)) return true;
        }

        return false;
    }

    public Payment getPayment(Long paymentId) {
        for(Payment p : payments) {
            if(paymentId.equals(p.getId())) return p;
        }

        throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    public Payment getPaymentByPgPaymentId(String pgPaymentId) {
        for(Payment p : payments) {
            if(pgPaymentId.equals(p.getPgPaymentId())) return p;
        }

        throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }

    public List<Payment> readyPayments() {
        return payments.stream().filter(Payment::isReady).toList();
    }

    private void ensureNoCoverInProgress() {
        for(Payment p : payments) {
            if(p.getType() == PaymentType.COVER && p.isReady()) throw new BusinessException(PaymentErrorCode.COVER_ALREADY_IN_PROGRESS);
        }
    }

    private void refreshStatus() {
        if(remainingBalance == 0) {
            status = PaymentGroupStatus.COMPLETED;
            return;
        }

        status = readyPayments().isEmpty() ? PaymentGroupStatus.PARTIALLY_FAILED : PaymentGroupStatus.IN_PROGRESS;
    }

    private String firstOrderName() {
        return payments.isEmpty() ? "모여타 택시 동승" : payments.get(0).getOrderName();
    }

    /**
     *  영속 복원용
     */
    public static PaymentGroup restore(Long id, Long partyId, Long driverId, int fare, int passengerCount, int platformCharge, Instant createdAt,
                                       int remainingBalance, PaymentGroupStatus status, List<Payment> payments) {
        return new PaymentGroup(id, partyId, driverId, fare, passengerCount, platformCharge, createdAt, remainingBalance, status, payments);
    }
}
