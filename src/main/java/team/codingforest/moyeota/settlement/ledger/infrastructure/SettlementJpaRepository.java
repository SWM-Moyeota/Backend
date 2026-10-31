package team.codingforest.moyeota.settlement.ledger.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.settlement.ledger.domain.SettlementStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SettlementJpaRepository extends JpaRepository<SettlementEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SettlementEntity s where s.id = :id")
    Optional<SettlementEntity> findByIdForUpdate(@Param("id") Long id);

    Optional<SettlementEntity> findByTaxiDriverIdAndPeriodStart(Long taxiDriverId, Instant periodStart);

    @Query("select s.id from SettlementEntity s where s.status in :statuses order by s.id")
    List<Long> findIdsByStatusIn(@Param("statuses") Collection<SettlementStatus> statuses);
}
