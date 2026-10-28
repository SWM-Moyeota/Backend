package team.codingforest.moyeota.settlement.ledger.domain;

import java.util.List;
import java.util.Optional;

public interface Settlements {
    Settlement save(Settlement settlement);
    Optional<Settlement> findById(Long id);
    Optional<Settlement> findByIdForUpdate(Long id);
    Optional<Settlement> findByDriverAndPeriod(Long taxiDriverId, SettlementPeriod period);
    List<Long> findUnpaidIds();
}
