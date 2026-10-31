package team.codingforest.moyeota.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.payment.entity.enums.PaymentGroupStatus;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "payment_group")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_id")
    private Long matchId;

    @Column(name = "taxi_driver_id")
    private Long taxiDriverId;

    @Column(name = "passenger_count")
    private Integer passengerCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PaymentGroupStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private PaymentGroup(Long matchId, Long taxiDriverId, Integer passengerCount) {
        this.matchId = matchId;
        this.taxiDriverId = taxiDriverId;
        this.passengerCount = passengerCount;
        this.status = PaymentGroupStatus.PENDING;
    }

    public static PaymentGroup create(Long matchId, Long taxiDriverId, Integer passengerCount) {
        return new PaymentGroup(matchId, taxiDriverId, passengerCount);
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
