package team.codingforest.moyeota.settlement.ledger.domain;

import java.time.Instant;
import java.util.List;

public interface SettlementLedgers {
    SettlementLedger save(SettlementLedger ledger);
    boolean existsByPaymentGroupId(Long paymentGroupId);
    List<SettlementLedger> findBySettlementId(Long settlementId);
    List<SettlementLedger> findUnsettledByDriverIn(Long taxiDriverId, SettlementPeriod week);
    List<Long> findDriverIdsToSettle(SettlementPeriod week);
    int assignToSettlement(Long taxiDriverId, Instant cutoff, Long settlementId);
    int sumSettlementAmount(Long settlementId);
}
