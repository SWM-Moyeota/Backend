package team.codingforest.moyeota.payment.infrastructure.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseEntity;
import team.codingforest.moyeota.payment.domain.PaymentLog;
import team.codingforest.moyeota.payment.domain.PaymentStatus;
import team.codingforest.moyeota.payment.domain.PaymentType;

import java.time.Instant;

/** 상태 전이 기록 - append-only 라 updated_at 없음 */
@Entity
@Getter
@Table(name = "payment_log")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentLogEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private PaymentEntity payment;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;

    private String failCode;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private PaymentLogEntity(PaymentEntity payment, Integer amount, PaymentStatus status, PaymentType type, String failCode, Instant createdAt) {
        this.payment = payment;
        this.amount = amount;
        this.status = status;
        this.type = type;
        this.failCode = failCode;
        this.createdAt = createdAt;
    }

    public static PaymentLogEntity of(PaymentEntity payment, PaymentLog log) {
        return new PaymentLogEntity(payment, log.amount(), log.status(), log.type(), log.failCode(), log.createdAt());
    }

    public PaymentLog toDomain() {
        return new PaymentLog(getId(), amount, status, type, failCode, createdAt);
    }
}
