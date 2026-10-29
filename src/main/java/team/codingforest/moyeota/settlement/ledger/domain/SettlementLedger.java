package team.codingforest.moyeota.settlement.ledger.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class SettlementLedger {
    private final Long id;
    private final Long taxiDriverId;
    private final Long settlementId;
    private final Long matchId;
    private final Integer settlementAmount;
    private final Integer totalFare;
    private final Integer platformPromotion;
    private final Instant occurredAt;
    private final Instant createdAt;

    private SettlementLedger(Long id, Long taxiDriverId, Long settlementId, Long matchId, Integer settlementAmount,
            Integer totalFare, Integer platformPromotion, Instant occurredAt, Instant createdAt) {
        this.id = id;
        this.taxiDriverId = taxiDriverId;
        this.settlementId = settlementId;
        this.matchId = matchId;
        this.settlementAmount = settlementAmount;
        this.totalFare = totalFare;
        this.platformPromotion = platformPromotion;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
    }

    public static SettlementLedger record(Long taxiDriverId, Long matchId, int totalFare, int platformPromotion, Instant occurredAt) {
        if(taxiDriverId == null || matchId == null || occurredAt == null) {
            throw new BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_COMMAND);
        }
        if(totalFare <= 0) throw new BusinessException(SettlementErrorCode.INVALID_TOTAL_FARE);
        if(platformPromotion < 0) throw new BusinessException(SettlementErrorCode.INVALID_PLATFORM_PROMOTION);

        int settlementAmount = Math.addExact(totalFare, platformPromotion);
        return new SettlementLedger(null, taxiDriverId, null, matchId, settlementAmount, totalFare, platformPromotion,
                occurredAt.truncatedTo(ChronoUnit.MICROS), null);
    }

    public static SettlementLedger load(Long id, Long taxiDriverId, Long settlementId, Long matchId, Integer settlementAmount,
            Integer totalFare, Integer platformPromotion, Instant occurredAt, Instant createdAt) {
        return new SettlementLedger(id, taxiDriverId, settlementId, matchId, settlementAmount, totalFare, platformPromotion,
                occurredAt, createdAt);
    }

    public Long getId() {
        return id;
    }

    public Long getTaxiDriverId() {
        return taxiDriverId;
    }

    public Long getSettlementId() {
        return settlementId;
    }

    public Long getMatchId() {
        return matchId;
    }

    public Integer getSettlementAmount() {
        return settlementAmount;
    }

    public Integer getTotalFare() {
        return totalFare;
    }

    public Integer getPlatformPromotion() {
        return platformPromotion;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public LocalDate occurredDate() {
        return LocalDate.ofInstant(occurredAt, SettlementPeriod.ZONE);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
