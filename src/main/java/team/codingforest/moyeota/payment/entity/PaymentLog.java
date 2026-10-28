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
import team.codingforest.moyeota.payment.entity.enums.PaymentLogStatus;
import team.codingforest.moyeota.payment.entity.enums.PaymentType;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "payment_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentLog extends BaseEntity {

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "amount")
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentLogStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private PaymentType type;

    @Column(name = "fail_code")
    private String failCode;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private PaymentLog(
            Long paymentId,
            Integer amount,
            PaymentLogStatus status,
            PaymentType type,
            String failCode
    ) {
        this.paymentId = paymentId;
        this.amount = amount;
        this.status = status;
        this.type = type;
        this.failCode = failCode;
    }

    public static PaymentLog create(
            Long paymentId,
            Integer amount,
            PaymentLogStatus status,
            PaymentType type,
            String failCode
    ) {
        return new PaymentLog(paymentId, amount, status, type, failCode);
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
