package team.codingforest.moyeota.matching.faresplit.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FareSplitJpaRepository extends JpaRepository<FareSplitEntity, Long> {

    @Query("select s from FareSplitEntity s left join fetch s.transfers where s.partyId = :partyId")
    Optional<FareSplitEntity> findWithTransfersByPartyId(@Param("partyId") Long partyId);

    // 송금 목록을 방 합류 순으로 보여 주려고 읽는다 - 방 상세 멤버 정렬(PartyEntity.members)과 같은 기준
    @Query("select m.memberId from PartyMemberEntity m where m.party.id = :partyId order by m.createdAt asc, m.memberId asc")
    List<Long> findMemberIdsInJoinOrder(@Param("partyId") Long partyId);
}
