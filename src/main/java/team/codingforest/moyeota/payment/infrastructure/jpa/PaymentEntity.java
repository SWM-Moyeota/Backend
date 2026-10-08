package team.codingforest.moyeota.payment.infrastructure.jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.payment.domain.Currency;
import team.codingforest.moyeota.payment.domain.Payment;
import team.codingforest.moyeota.payment.domain.PaymentLog;
import team.codingforest.moyeota.payment.domain.PaymentStatus;
import team.codingforest.moyeota.payment.domain.PaymentType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "payment", indexes = {
        @Index(name = "idx_payment_user_id", columnList = "user_id"),
        @Index(name = "idx_payment_pg_payment_id", columnList = "pg_payment_id", unique = true)
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentEntity extends BaseTimeEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_group_id", nullable = false)
    private PaymentGroupEntity group;

    /** 결제사에 넘긴 식별자(가장 최근 시도) */
    @Column(name = "pg_payment_id")
    private String pgPaymentId;

    @Column(nullable = false)
    private Long userId;

    private Long paymentMethodId;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false)
    private String orderName;

    private String failCode;

    private Instant cancelAt;

    private String cancelReason;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PaymentLogEntity> logs = new ArrayList<>();

    private PaymentEntity(PaymentGroupEntity group, Payment payment) {
        this.group = group;
        this.pgPaymentId = payment.getPgPaymentId();
        this.userId = payment.getUserId();
        this.paymentMethodId = payment.getPaymentMethodId();
        this.amount = payment.getAmount();
        this.status = payment.getStatus();
        this.type = payment.getType();
        this.currency = payment.getCurrency();
        this.orderName = payment.getOrderName();
        this.failCode = payment.getFailCode();
        this.cancelAt = payment.getCanceledAt();
        this.cancelReason = payment.getCancelReason();
    }

    public static PaymentEntity from(PaymentGroupEntity group, Payment payment) {
        PaymentEntity entity = new PaymentEntity(group, payment);
        entity.appendNewLogs(payment);
        return entity;
    }

    public Payment toDomain() {
        return Payment.restore(getId(), userId, amount, type, currency, orderName, getCreatedAt(), pgPaymentId, paymentMethodId, status, failCode, cancelAt, cancelReason,
                logs.stream().map(PaymentLogEntity::toDomain).toList());
    }

    public void update(Payment payment) {
        this.pgPaymentId = payment.getPgPaymentId();
        this.paymentMethodId = payment.getPaymentMethodId();
        this.status = payment.getStatus();
        this.failCode = payment.getFailCode();
        this.cancelAt = payment.getCanceledAt();
        this.cancelReason = payment.getCancelReason();
        appendNewLogs(payment);
    }

    /** id 없는 로그 = 이번 변경에서 새로 생긴 전이 기록 */
    private void appendNewLogs(Payment payment) {
        for(PaymentLog log : payment.getLogs()) {
            if(log.id() == null) logs.add(PaymentLogEntity.of(this, log));
        }
    }
}
