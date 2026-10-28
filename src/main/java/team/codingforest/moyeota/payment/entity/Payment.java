package team.codingforest.moyeota.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseEntity;
import team.codingforest.moyeota.payment.entity.enums.PaymentCurrency;
import team.codingforest.moyeota.payment.entity.enums.PaymentStatus;
import team.codingforest.moyeota.payment.entity.enums.PaymentType;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Column(name = "pg_payment_id")
    private String pgPaymentId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "payment_group_id")
    private Long paymentGroupId;

    @Column(name = "payment_method_id")
    private Long paymentMethodId;

    @Column(name = "amount")
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private PaymentType type;

    @Column(name = "order_name")
    private String orderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency")
    private PaymentCurrency currency;

    @Column(name = "fail_code")
    private String failCode;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private Payment(
            String pgPaymentId,
            Long userId,
            Long paymentGroupId,
            Long paymentMethodId,
            Integer amount,
            PaymentType type,
            PaymentCurrency currency,
            String orderName
    ) {
        this.pgPaymentId = pgPaymentId;
        this.userId = userId;
        this.paymentGroupId = paymentGroupId;
        this.paymentMethodId = paymentMethodId;
        this.amount = amount;
        this.type = type;
        this.currency = currency;
        this.orderName = orderName;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment create(
            String pgPaymentId,
            Long userId,
            Long paymentGroupId,
            Long paymentMethodId,
            Integer amount,
            PaymentType type,
            PaymentCurrency currency,
            String orderName
    ) {
        return new Payment(
                pgPaymentId,
                userId,
                paymentGroupId,
                paymentMethodId,
                amount,
                type,
                currency,
                orderName
        );
    }

    @PrePersist
    private void initializeTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    private void updateTimestamp() {
        updatedAt = LocalDateTime.now();
    }
}
