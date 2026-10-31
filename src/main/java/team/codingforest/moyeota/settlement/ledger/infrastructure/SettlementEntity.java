package team.codingforest.moyeota.settlement.ledger.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.settlement.ledger.domain.Settlement;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementStatus;

import java.time.Instant;

@Entity
@Getter
@Table(name = "settlement",
        uniqueConstraints = @UniqueConstraint(name = "uk_settlement_driver_period", columnNames = {"taxi_driver_id", "period_start"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "taxi_driver_id", nullable = false, updatable = false)
    private Long taxiDriverId;

    @Column(name = "period_start", nullable = false, updatable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false, updatable = false)
    private Instant periodEnd;

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SettlementStatus status;

    private SettlementEntity(Long taxiDriverId, Instant periodStart, Instant periodEnd, Integer amount, SettlementStatus status) {
        this.taxiDriverId = taxiDriverId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.amount = amount;
        this.status = status;
    }

    public static SettlementEntity from(Settlement settlement) {
        return new SettlementEntity(settlement.getTaxiDriverId(), settlement.getPeriodStart(), settlement.getPeriodEnd(),
                settlement.getAmount(), settlement.getStatus());
    }

    public SettlementEntity update(Settlement settlement) {
        this.amount = settlement.getAmount();
        this.status = settlement.getStatus();
        return this;
    }

    public Settlement toDomain() {
        return Settlement.load(id, taxiDriverId, periodStart, periodEnd, amount, status, getCreatedAt(), getUpdatedAt());
    }
}
