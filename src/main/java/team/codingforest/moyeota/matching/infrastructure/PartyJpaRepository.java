package team.codingforest.moyeota.matching.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.matching.domain.Party;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

import java.util.List;

@Repository
public interface PartyJpaRepository extends JpaRepository<PartyEntity, Long> {
    @Query("select p from PartyEntity p left join fetch p.members where p.status = :status")
    List<PartyEntity> findAllByStatus(@Param("status") PartyStatus status);
}
