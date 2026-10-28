package team.codingforest.moyeota.settlement.ledger.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface Payouts {
    Payout save(Payout payout);
    Optional<Payout> findById(Long id);
    Optional<Payout> findByIdForUpdate(Long id);
    boolean existsActiveBySettlementId(Long settlementId);
    int countFailedBySettlementId(Long settlementId);
    Optional<Payout> findLatestBySettlementId(Long settlementId);
    List<Long> findStaleIds(Instant requestedBefore);
}
