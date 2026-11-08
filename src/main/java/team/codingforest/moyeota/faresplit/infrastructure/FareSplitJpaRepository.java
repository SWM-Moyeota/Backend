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

    // 송금 행까지 join 해 잠그지 않는다 - 정산 행 하나만 잠그고 송금은 그 뒤에 읽는다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FareSplitEntity s where s.partyId = :partyId")
    Optional<FareSplitEntity> findByPartyIdForUpdate(@Param("partyId") Long partyId);

    @Query("""
            select s.partyId, s.payerId, s.amountPerPerson, t.sentAt, t.confirmedAt, s.createdAt
            from FareSplitTransferEntity t
            join t.fareSplit s
            where t.memberId = :memberId and s.status = team.codingforest.moyeota.faresplit.domain.FareSplitStatus.OPEN
            order by s.createdAt desc, s.partyId desc
            """)
    List<OpenTransfer> findOpenTransfersBySender(@Param("memberId") Long memberId);

    // 송금 행을 펼치지 않고 정산 한 건당 한 줄 - 보내는 사람 수와 내가 보내는 사람인지는 송금 PK(match_id, user_id)로 센다
    @Query("""
            select s.partyId, s.payerId, s.totalFare, s.amountPerPerson, size(s.transfers)
            from FareSplitEntity s
            where s.partyId in :partyIds
                and (s.payerId = :memberId
                    or exists (select t.memberId from FareSplitTransferEntity t where t.fareSplit.partyId = s.partyId and t.memberId = :memberId))
            """)
    List<FareShare> findShares(@Param("memberId") Long memberId, @Param("partyIds") Collection<Long> partyIds);

    // 아직 안 보낸(NOT_SENT) 송금 중 마지막 송금 요청이 requestedBefore 이전인 것
    @Query("""
            select distinct s.partyId from FareSplitTransferEntity t
            join t.fareSplit s
            where s.status = team.codingforest.moyeota.faresplit.domain.FareSplitStatus.OPEN
                and s.accountNumber is not null
                and t.sentAt is null
                and t.confirmedAt is null
                and t.lastRequestedAt <= :requestedBefore
            order by s.partyId
            """)
    List<Long> findTransferRequestTargetIds(@Param("requestedBefore") Instant requestedBefore);

    @Query("""
            select distinct s.partyId from FareSplitTransferEntity t
            join t.fareSplit s
            where s.status = team.codingforest.moyeota.faresplit.domain.FareSplitStatus.OPEN
                and t.sentAt is not null
                and t.confirmedAt is null
                and t.confirmRequestedAt is null
                and t.sentAt <= :sentBefore
            order by s.partyId
            """)
    List<Long> findConfirmRequestTargetIds(@Param("sentBefore") Instant sentBefore);

    // idx_fare_split_status_created_at
    @Query("""
            select s.partyId from FareSplitEntity s
            where s.status = team.codingforest.moyeota.faresplit.domain.FareSplitStatus.OPEN
                and s.createdAt <= :createdBefore
            order by s.createdAt, s.partyId
            """)
    List<Long> findExpireTargetIds(@Param("createdBefore") Instant createdBefore);

    @Query("""
            select s.partyId from FareSplitEntity s
            where s.payerId = :payerId
                and s.status = team.codingforest.moyeota.faresplit.domain.FareSplitStatus.OPEN
            order by s.partyId
            """)
    List<Long> findOpenIdsByPayer(@Param("payerId") Long payerId);
}
