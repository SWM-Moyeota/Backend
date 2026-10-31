package team.codingforest.moyeota.settlement.ledger.domain;

import java.util.Optional;

public interface PayoutAccounts {
    Optional<PayoutAccount> findByDriverId(Long taxiDriverId);
}
