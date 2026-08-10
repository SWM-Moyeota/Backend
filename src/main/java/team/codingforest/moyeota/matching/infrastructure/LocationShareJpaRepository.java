package team.codingforest.moyeota.matching.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LocationShareJpaRepository extends JpaRepository<LocationShareEntity, Long> {
    @Query("select ls from LocationShareEntity ls where ls.partyId = :partyId and ls.memberId = :memberId and ls.stoppedAt is null")
    Optional<LocationShareEntity> findOngoing(@Param("partyId") Long partyId, @Param("memberId") Long memberId);

    @Query("select ls from LocationShareEntity ls where ls.partyId = :partyId and ls.stoppedAt is null")
    List<LocationShareEntity> findAllOngoing(@Param("partyId") Long partyId);
}
