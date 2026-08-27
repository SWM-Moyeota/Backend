package team.codingforest.moyeota.payment.domain.entity;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;

import java.time.Instant;

@Table(name="payment")
@Entity
@NoArgsConstructor
public class Payment extends BaseTimeEntity {

    private String pgTxId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long paymentGroupId;

    @Column(nullable = false)
    private Long paymentMethodId;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

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

    private Payment(Long userId, String pgTxId, Long paymentGroupId, Long paymentMethodId,
                    int amount, PaymentStatus status, PaymentType type,
                    Currency currency, String orderName) {
        this.userId = userId;
        this.pgTxId = pgTxId;
        this.paymentGroupId = paymentGroupId;
        this.paymentMethodId = paymentMethodId;
        this.amount = amount;
        this.status = status;
        this.type = type;
        this.currency = currency;
        this.orderName = orderName;
    }

    public static Payment of(Long userId, String pgTxId, Long paymentGroupId, Long paymentMethodId,
                             int amount, PaymentStatus status, PaymentType type,
                             Currency currency, String orderName) {
        return new Payment(userId, pgTxId, paymentGroupId, paymentMethodId, amount, status, type, currency, orderName);
    }

    public static Payment pending(Long userId, Long paymentGroupId, Long paymentMethodId, PaymentType type, int amount, String orderName) {
        return new Payment(userId, null, paymentGroupId, paymentMethodId, amount, PaymentStatus.PENDING, type, Currency.KRW, orderName);
    }

}
