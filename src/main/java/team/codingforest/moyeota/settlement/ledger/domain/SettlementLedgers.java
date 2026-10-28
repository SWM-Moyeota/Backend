package team.codingforest.moyeota.settlement.ledger.domain;

import java.time.Instant;
import java.util.List;

public interface SettlementLedgers {
    SettlementLedger save(SettlementLedger ledger);
    boolean existsByMatchId(Long matchId);
    List<SettlementLedger> findBySettlementId(Long settlementId);
    List<SettlementLedger> findUnsettledByDriver(Long taxiDriverId);
    List<Long> findDriverIdsToSettle(SettlementPeriod week, long afterDriverId, int limit);
    int assignToSettlement(Long taxiDriverId, Instant cutoff, Long settlementId);
    int sumSettlementAmount(Long settlementId);
}
