package team.codingforest.moyeota.settlement.account.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DriverPayoutAccountJpaRepository extends JpaRepository<DriverPayoutAccountEntity, Long> {
    Optional<DriverPayoutAccountEntity> findByTaxiDriverId(Long taxiDriverId);

    Optional<DriverPayoutAccountEntity> findBySellerId(String sellerId);
}
