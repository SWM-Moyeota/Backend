package team.codingforest.moyeota.faresplit.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.faresplit.domain.FareShare;
import team.codingforest.moyeota.faresplit.domain.OpenTransfer;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FareSplitJpaRepository extends JpaRepository<FareSplitEntity, Long> {

    @Query("select s from FareSplitEntity s left join fetch s.transfers where s.partyId = :partyId")
    Optional<FareSplitEntity> findWithTransfersByPartyId(@Param("partyId") Long partyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FareSplitEntity s where s.partyId = :partyId")
    Optional<FareSplitEntity> findByPartyIdForUpdate(@Param("partyId") Long partyId);

    @Query("""
            select s.partyId, s.payerId, t.payment, t.sentAt, t.confirmedAt, s.createdAt
            from FareSplitTransferEntity t
            join t.fareSplit s
            where t.memberId = :memberId
                and s.payerId <> :memberId
                and s.status = team.codingforest.moyeota.faresplit.domain.enums.FareSplitStatus.OPEN
            order by s.createdAt desc, s.partyId desc
            """)
    List<OpenTransfer> findOpenTransfersBySender(@Param("memberId") Long memberId);

    @Query("""
            select s.partyId, s.totalFare, t.payment
            from FareSplitTransferEntity t
            join t.fareSplit s
            where t.memberId = :memberId and s.partyId in :partyIds
            """)
    List<FareShare> findShares(@Param("memberId") Long memberId, @Param("partyIds") Collection<Long> partyIds);

    @Query(value = """
            select distinct s.match_id
            from fare_split_transfer t
            join fare_split s on s.match_id = t.match_id
            where s.status = 'OPEN'
                and s.account_number is not null
                and t.sent_at is null
                and t.confirmed_at is null
                and t.request_count < :maxRequests
                and s.created_at + t.request_count * :intervalSeconds * interval '1' second <= :now
            order by s.match_id
            """, nativeQuery = true)
    List<Long> findTransferRequestTargetIds(@Param("now") Instant now, @Param("maxRequests") int maxRequests, @Param("intervalSeconds") long intervalSeconds);

    @Query("""
            select s.partyId from FareSplitEntity s
            where s.status = team.codingforest.moyeota.faresplit.domain.enums.FareSplitStatus.OPEN
                and s.createdAt <= :createdBefore
            order by s.createdAt, s.partyId
            """)
    List<Long> findExpireTargetIds(@Param("createdBefore") Instant createdBefore);

    @Query("""
            select s.partyId from FareSplitEntity s
            where s.payerId = :payerId
                and s.status = team.codingforest.moyeota.faresplit.domain.enums.FareSplitStatus.OPEN
            order by s.partyId
            """)
    List<Long> findOpenIdsByPayer(@Param("payerId") Long payerId);
}
