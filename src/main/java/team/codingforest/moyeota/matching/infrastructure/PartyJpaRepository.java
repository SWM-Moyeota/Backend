package team.codingforest.moyeota.matching.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

import java.util.Collection;
import java.util.List;

public interface PartyJpaRepository extends JpaRepository<PartyEntity, Long> {
    @Query("select p from PartyEntity p left join fetch p.members where p.status = :status")
    List<PartyEntity> findAllByStatus(@Param("status") PartyStatus status);

    @Query("select count(m) > 0 from PartyMemberEntity m where m.memberId = :memberId and m.party.status in :statuses")
    boolean existsByMemberIdAndStatusIn(@Param("memberId") Long memberId, @Param("statuses") Collection<PartyStatus> statuses);
}
