package team.codingforest.moyeota.settlement.ledger.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseCreatedTimeEntity;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementLedger;

import java.time.Instant;

@Entity
@Getter
@Table(name = "settlement_ledger",
        uniqueConstraints = @UniqueConstraint(name = "uk_settlement_ledger_match", columnNames = "match_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementLedgerEntity extends BaseCreatedTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "taxi_driver_id", nullable = false, updatable = false)
    private Long taxiDriverId;

    @Column(name = "settlement_id")
    private Long settlementId;

    @Column(name = "match_id", nullable = false, updatable = false)
    private Long matchId;

    @Column(name = "settlement_amount", nullable = false, updatable = false)
    private Integer settlementAmount;

    @Column(name = "total_fare", nullable = false, updatable = false)
    private Integer totalFare;

    @Column(name = "platform_promotion", nullable = false, updatable = false)
    private Integer platformPromotion;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    private SettlementLedgerEntity(Long taxiDriverId, Long settlementId, Long matchId, Integer settlementAmount, Integer totalFare,
                                   Integer platformPromotion, Instant occurredAt) {
        this.taxiDriverId = taxiDriverId;
        this.settlementId = settlementId;
        this.matchId = matchId;
        this.settlementAmount = settlementAmount;
        this.totalFare = totalFare;
        this.platformPromotion = platformPromotion;
        this.occurredAt = occurredAt;
    }

    public static SettlementLedgerEntity from(SettlementLedger ledger) {
        return new SettlementLedgerEntity(ledger.getTaxiDriverId(), ledger.getSettlementId(), ledger.getMatchId(),
                ledger.getSettlementAmount(), ledger.getTotalFare(), ledger.getPlatformPromotion(), ledger.getOccurredAt());
    }

    public SettlementLedger toDomain() {
        return SettlementLedger.load(id, taxiDriverId, settlementId, matchId, settlementAmount, totalFare, platformPromotion,
                occurredAt, getCreatedAt());
    }
}
