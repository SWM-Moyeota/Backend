package team.codingforest.moyeota.settlement.ledger.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PayoutJpaRepository extends JpaRepository<PayoutEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PayoutEntity p where p.id = :id")
    Optional<PayoutEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsBySettlementIdAndStatusIn(Long settlementId, Collection<PayoutStatus> statuses);

    long countBySettlementIdAndStatus(Long settlementId, PayoutStatus status);

    Optional<PayoutEntity> findFirstBySettlementIdOrderByIdDesc(Long settlementId);

    @Query("""
            select p.id from PayoutEntity p
            where p.id > :afterId
                and ((p.status = :requestedStatus and p.createdAt < :requestedBefore) or p.status = :unknownStatus)
            order by p.id
            """)
    List<Long> findStaleIds(@Param("afterId") long afterId,
                            @Param("requestedStatus") PayoutStatus requestedStatus, @Param("requestedBefore") Instant requestedBefore,
                            @Param("unknownStatus") PayoutStatus unknownStatus, Limit limit);
}
