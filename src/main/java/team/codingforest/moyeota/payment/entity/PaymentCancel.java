package team.codingforest.moyeota.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "payment_cancel")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancel {

    @Id
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "cancel_at")
    private LocalDateTime cancelAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private PaymentCancel(Long paymentId, LocalDateTime cancelAt, String cancelReason) {
        this.paymentId = paymentId;
        this.cancelAt = cancelAt;
        this.cancelReason = cancelReason;
    }

    public static PaymentCancel create(Long paymentId, LocalDateTime cancelAt, String cancelReason) {
        return new PaymentCancel(paymentId, cancelAt, cancelReason);
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
