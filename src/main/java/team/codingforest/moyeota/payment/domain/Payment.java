package team.codingforest.moyeota.payment.domain;

import lombok.Getter;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 *  결제 그룹 안의 개별 청구 한 건. 상태 전이는 반드시 PaymentGroup 을 거친다(잔액·그룹 상태가 같이 움직여야 하므로).
 *  pgPaymentId 는 가장 최근 시도의 결제사 식별자 - 재시도마다 바뀐다.
 */
@Getter
public class Payment {
    private final Long id;
    private final Long userId;
    private final int amount;
    private final PaymentType type;
    private final Currency currency;
    private final String orderName;
    private final Instant createdAt;
    private String pgPaymentId;
    private Long paymentMethodId;
    private PaymentStatus status;
    private String failCode;
    private Instant canceledAt;
    private String cancelReason;
    private final List<PaymentLog> logs;

    private Payment(Long id, Long userId, int amount, PaymentType type, Currency currency, String orderName, Instant createdAt,
                    String pgPaymentId, Long paymentMethodId, PaymentStatus status, String failCode, Instant canceledAt, String cancelReason, List<PaymentLog> logs) {
        this.id = id;
        this.userId = userId;
        this.amount = amount;
        this.type = type;
        this.currency = currency;
        this.orderName = orderName;
        this.createdAt = createdAt;
        this.pgPaymentId = pgPaymentId;
        this.paymentMethodId = paymentMethodId;
        this.status = status;
        this.failCode = failCode;
        this.canceledAt = canceledAt;
        this.cancelReason = cancelReason;
        this.logs = new ArrayList<>(logs);
    }

    static Payment ready(Long userId, int amount, PaymentType type, String orderName, Instant now) {
        if(amount <= 0) throw new BusinessException(PaymentErrorCode.INVALID_AMOUNT);

        Payment payment = new Payment(null, userId, amount, type, Currency.KRW, orderName, now, null, null, PaymentStatus.READY, null, null, null, List.of());
        payment.record(now);

        return payment;
    }

    /** 결제사에 청구를 보내기 직전 - 어떤 수단으로, 어떤 식별자로 보냈는지 남긴다 */
    void attempt(Long paymentMethodId, String pgPaymentId) {
        if(status != PaymentStatus.READY) throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_READY);

        this.paymentMethodId = paymentMethodId;
        this.pgPaymentId = pgPaymentId;
        this.failCode = null;
    }

    void markPaid(Instant now) {
        if(status != PaymentStatus.READY) throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_READY);

        this.status = PaymentStatus.PAID;
        this.failCode = null;
        record(now);
    }

    void markFailed(String failCode, Instant now) {
        if(status != PaymentStatus.READY) throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_READY);

        this.status = PaymentStatus.FAILED;
        this.failCode = failCode;
        record(now);
    }

    /** 실패 건을 다시 청구 대기로. 성공·취소·대납 종결된 건은 되돌릴 수 없다 */
    void retry(Instant now) {
        if(status != PaymentStatus.FAILED) throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_RETRYABLE);

        this.status = PaymentStatus.READY;
        this.failCode = null;
        this.pgPaymentId = null;
        record(now);
    }

    void markCovered(Instant now) {
        if(status != PaymentStatus.FAILED) throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_RETRYABLE);

        this.status = PaymentStatus.COVERED;
        record(now);
    }

    void cancel(String reason, Instant canceledAt) {
        if(status != PaymentStatus.PAID) throw new BusinessException(PaymentErrorCode.PAYMENT_NOT_PAID);

        this.status = PaymentStatus.CANCELED;
        this.cancelReason = reason;
        this.canceledAt = canceledAt;
        record(canceledAt);
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isReady() {
        return status == PaymentStatus.READY;
    }

    public boolean isFailed() {
        return status == PaymentStatus.FAILED;
    }

    /** 이 결제에 담긴 운행 요금 몫. 분담금엔 플랫폼 이용료가 얹혀 있어 그만큼 빼야 한다 */
    int farePortion(int platformCharge) {
        return type == PaymentType.SHARE ? amount - platformCharge : amount;
    }

    private void record(Instant at) {
        logs.add(PaymentLog.of(this, at));
    }

    /**
     *  영속 복원용
     */
    public static Payment restore(Long id, Long userId, int amount, PaymentType type, Currency currency, String orderName, Instant createdAt,
                                  String pgPaymentId, Long paymentMethodId, PaymentStatus status, String failCode, Instant canceledAt, String cancelReason, List<PaymentLog> logs) {
        return new Payment(id, userId, amount, type, currency, orderName, createdAt, pgPaymentId, paymentMethodId, status, failCode, canceledAt, cancelReason, logs);
    }
}
