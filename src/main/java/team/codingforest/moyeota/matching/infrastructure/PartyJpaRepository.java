package team.codingforest.moyeota.matching.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.matching.domain.enums.PartyStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PartyJpaRepository extends JpaRepository<PartyEntity, Long> {
    @Query("select p from PartyEntity p left join fetch p.members where p.status = :status")
    List<PartyEntity> findAllByStatus(@Param("status") PartyStatus status);

    @Query("select count(m) > 0 from PartyMemberEntity m where m.memberId = :memberId and m.party.status in :statuses")
    boolean existsByMemberIdAndStatusIn(@Param("memberId") Long memberId, @Param("statuses") Collection<PartyStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PartyEntity p where p.id = :id")
    Optional<PartyEntity> findByForUpdate(@Param("id") Long id);

    @Query("select p from PartyEntity p where p.status = :status")
    List<PartyEntity> findTargetsByStatus(@Param("status") PartyStatus status);

    @Query("select count(p) > 0 from PartyEntity p where p.taxiDriverId = :driverId and p.status in :statuses")
    boolean existsByTaxiDriverIdStatus(@Param("driverId") Long driverId, @Param("statuses") Collection<PartyStatus> statuses);

    @Query("""
        select p from PartyEntity p left join fetch p.members where p.status = :status
            and p.departureLat between :swLat and :neLat
            and p.destinationLng between :swLng and :neLng
""")
    List<PartyEntity> findAllByStatusWithinBounds(@Param("status") PartyStatus status, @Param("swLat") double swLat, @Param("neLat") double neLat,
                                                  @Param("swLng") double swLng, @Param("neLng") double neLng);
}