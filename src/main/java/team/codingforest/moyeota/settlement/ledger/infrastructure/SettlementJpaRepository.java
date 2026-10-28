package team.codingforest.moyeota.settlement.ledger.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutStatus;
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

    @Query("""
            select s.id from SettlementEntity s
            where s.id > :afterId
                and s.amount > 0
                and (s.status in :readyStatuses
                    or (s.status = :failedStatus
                        and (select count(f) from PayoutEntity f
                             where f.settlementId = s.id and f.status = :failedPayoutStatus) < :maxPayoutAttempts))
                and not exists (select a.id from PayoutEntity a
                                where a.settlementId = s.id and a.status in :activePayoutStatuses)
            order by s.id
            """)
    List<Long> findPayableIds(@Param("afterId") long afterId,
                              @Param("readyStatuses") Collection<SettlementStatus> readyStatuses,
                              @Param("failedStatus") SettlementStatus failedStatus,
                              @Param("failedPayoutStatus") PayoutStatus failedPayoutStatus,
                              @Param("maxPayoutAttempts") long maxPayoutAttempts,
                              @Param("activePayoutStatuses") Collection<PayoutStatus> activePayoutStatuses,
                              Limit limit);
}
