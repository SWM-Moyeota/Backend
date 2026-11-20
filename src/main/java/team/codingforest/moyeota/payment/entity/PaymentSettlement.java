package team.codingforest.moyeota.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "payment_settlement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentSettlement {

    @Id
    @Column(name = "payment_group_id")
    private Long paymentGroupId;

    @Column(name = "total_fare")
    private Integer totalFare;

    @Column(name = "fare")
    private Integer fare;

    @Column(name = "remaining_balance")
    private Integer remainingBalance;

    @Column(name = "platform_charge")
    private Integer platformCharge;

    @Column(name = "taxi_driver_id")
    private Long taxiDriverId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    private PaymentSettlement(
            Long paymentGroupId,
            Integer totalFare,
            Integer fare,
            Integer remainingBalance,
            Integer platformCharge,
            Long taxiDriverId
    ) {
        this.paymentGroupId = paymentGroupId;
        this.totalFare = totalFare;
        this.fare = fare;
        this.remainingBalance = remainingBalance;
        this.platformCharge = platformCharge;
        this.taxiDriverId = taxiDriverId;
    }

    public static PaymentSettlement create(
            Long paymentGroupId,
            Integer totalFare,
            Integer fare,
            Integer remainingBalance,
            Integer platformCharge,
            Long taxiDriverId
    ) {
        return new PaymentSettlement(
                paymentGroupId,
                totalFare,
                fare,
                remainingBalance,
                platformCharge,
                taxiDriverId
        );
    }

    @PrePersist
    private void initializeTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
    }
}
