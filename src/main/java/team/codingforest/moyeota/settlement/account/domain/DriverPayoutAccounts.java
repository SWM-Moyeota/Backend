package team.codingforest.moyeota.settlement.account.domain;

import java.util.Optional;

public interface DriverPayoutAccounts {
    DriverPayoutAccount save(DriverPayoutAccount account);

    void delete(DriverPayoutAccount account);

    Optional<DriverPayoutAccount> findByDriverId(Long taxiDriverId);

    Optional<DriverPayoutAccount> findBySellerId(String sellerId);
}
