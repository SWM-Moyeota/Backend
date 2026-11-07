package team.codingforest.moyeota.matching.faresplit.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.matching.faresplit.domain.FareSplitStatus;
import team.codingforest.moyeota.matching.faresplit.domain.OpenTransfer;

import java.util.List;
import java.util.Optional;

public interface FareSplitJpaRepository extends JpaRepository<FareSplitEntity, Long> {

    @Query("select s from FareSplitEntity s left join fetch s.transfers where s.partyId = :partyId")
    Optional<FareSplitEntity> findWithTransfersByPartyId(@Param("partyId") Long partyId);

    // 송금 행까지 join 해 잠그지 않는다 - 정산 행 하나만 잠그고 송금은 그 뒤에 읽는다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FareSplitEntity s where s.partyId = :partyId")
    Optional<FareSplitEntity> findByPartyIdForUpdate(@Param("partyId") Long partyId);

    // 송금 목록을 방 합류 순으로 보여 주려고 읽는다 - 방 상세 멤버 정렬(PartyEntity.members)과 같은 기준
    @Query("select m.memberId from PartyMemberEntity m where m.party.id = :partyId order by m.createdAt asc, m.memberId asc")
    List<Long> findMemberIdsInJoinOrder(@Param("partyId") Long partyId);

    @Query("""
            select s.partyId, p.departure, p.destination, s.payerId, s.amountPerPerson, t.sentAt, t.confirmedAt, s.createdAt
            from FareSplitTransferEntity t
            join t.fareSplit s
            join PartyEntity p on p.id = s.partyId
            where t.memberId = :memberId and s.status = :status
            order by s.createdAt desc, s.partyId desc
            """)
    List<OpenTransfer> findTransfersBySender(@Param("memberId") Long memberId, @Param("status") FareSplitStatus status);
}
