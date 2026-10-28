package team.codingforest.moyeota.settlement.ledger.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.settlement.exception.SettlementErrorCode;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

public class Settlement {
    private static final Set<SettlementStatus> PAYOUT_STAGES = EnumSet.of(SettlementStatus.CONFIRMED, SettlementStatus.HOLD, SettlementStatus.FAILED);

    private final Long id;
    private final Long taxiDriverId;
    private final Instant periodStart;
    private final Instant periodEnd;
    private Integer amount;
    private SettlementStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Settlement(Long id, Long taxiDriverId, Instant periodStart, Instant periodEnd, Integer amount,
            SettlementStatus status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.taxiDriverId = taxiDriverId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Settlement create(Long taxiDriverId, SettlementPeriod period) {
        return new Settlement(null, taxiDriverId, period.start(), period.end(), 0, SettlementStatus.CONFIRMED, null, null);
    }

    public static Settlement load(Long id, Long taxiDriverId, Instant periodStart, Instant periodEnd, Integer amount,
            SettlementStatus status, Instant createdAt, Instant updatedAt) {
        return new Settlement(id, taxiDriverId, periodStart, periodEnd, amount, status, createdAt, updatedAt);
    }

    public void fixAmount(int ledgerTotal) {
        if(status != SettlementStatus.CONFIRMED) throw new BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_STATUS);
        if(ledgerTotal <= 0) throw new IllegalArgumentException("정산 금액은 0보다 커야 합니다. ledgerTotal=" + ledgerTotal);

        this.amount = ledgerTotal;
    }

    public boolean isPayable() {
        return isInPayoutStage() && amount > 0;
    }

    public void hold() {
        if(!isInPayoutStage()) throw new BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_STATUS);

        this.status = SettlementStatus.HOLD;
    }

    public void startPayout() {
        if(!isInPayoutStage()) throw new BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_STATUS);

        this.status = SettlementStatus.CONFIRMED;
    }

    public void paid() {
        if(status != SettlementStatus.CONFIRMED) throw new BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_STATUS);

        this.status = SettlementStatus.PAID;
    }

    public void payoutFailed() {
        if(status != SettlementStatus.CONFIRMED) throw new BusinessException(SettlementErrorCode.INVALID_SETTLEMENT_STATUS);

        this.status = SettlementStatus.FAILED;
    }

    public SettlementPeriod period() {
        return new SettlementPeriod(periodStart, periodEnd);
    }

    public boolean isOwnedBy(Long taxiDriverId) {
        return taxiDriverId != null && taxiDriverId.equals(this.taxiDriverId);
    }

    boolean isInPayoutStage() {
        return PAYOUT_STAGES.contains(status);
    }

    public Long getId() {
        return id;
    }

    public Long getTaxiDriverId() {
        return taxiDriverId;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public Integer getAmount() {
        return amount;
    }

    public SettlementStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
