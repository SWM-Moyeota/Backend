package team.codingforest.moyeota.matching.party.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.PartySummary;
import team.codingforest.moyeota.matching.party.dto.NearbyPartySummary;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PartyJpaRepository extends JpaRepository<PartyEntity, Long> {
    @Query("select p from PartyEntity p left join fetch p.members where p.status = :status")
    List<PartyEntity> findAllByStatus(@Param("status") PartyStatus status);

    @Query("select count(m) > 0 from PartyMemberEntity m where m.memberId = :memberId and m.party.status in :statuses")
    boolean existsByMemberIdAndStatusIn(@Param("memberId") Long memberId, @Param("statuses") Collection<PartyStatus> statuses);

    @Query("select p from PartyEntity p left join fetch p.members where p.id = :id")
    Optional<PartyEntity> findWithMembersById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PartyEntity p left join fetch p.members where p.id = :id")
    Optional<PartyEntity> findByForUpdate(@Param("id") Long id);

    @Query("select p from PartyEntity p where p.status = :status")
    List<PartyEntity> findTargetsByStatus(@Param("status") PartyStatus status);

    @Query("select count(p) > 0 from PartyEntity p where p.taxiDriverId = :driverId and p.status in :statuses")
    boolean existsByTaxiDriverIdStatus(@Param("driverId") Long driverId, @Param("statuses") Collection<PartyStatus> statuses);

    // 지도 목록은 인원수만 필요하다 - 멤버를 join fetch 하면 방 하나가 멤버 수만큼 행으로 불어나고 route(TEXT)까지 행마다 딸려 온다
    @Query("""
            select p.id, p.departure, p.destination,
                   (select count(m) from PartyMemberEntity m where m.party = p),
                   p.capacity, p.status, p.departureLat, p.departureLng
            from PartyEntity p
            where p.status = :status
                and p.departureLat between :swLat and :neLat
                and p.departureLng between :swLng and :neLng
            order by p.createdAt desc, p.id desc
            """)
    List<PartySummary> findSummariesWithinBounds(@Param("status") PartyStatus status, @Param("swLat") double swLat, @Param("neLat") double neLat,
                                                 @Param("swLng") double swLng, @Param("neLng") double neLng, Limit limit);

    @Query("""
                    select m.memberId as memberId, count(m) as count
                    from PartyMemberEntity m
                    where m.memberId in :memberIds and m.party.status = :status
                    group by m.memberId
            """)
    List<MemberRideCount> countByMemberIdsAndStatus(@Param("memberIds") Collection<Long> memberIds, @Param("status") PartyStatus status);

    @Query("select p.id from PartyEntity p where p.status = :status and p.completedAt < :before")
    List<Long> findIdsByStatusAndCompletedBefore(@Param("status") PartyStatus status, @Param("before") Instant before);

    @Query(value = """
            SELECT m.id, m.departure_lat, m.departure_lng, m.destination_lat, m.destination_lng, m.departure, m.destination, m.capacity
            FROM match_room m
            WHERE m.status = 'ACTIVE'
              AND ST_DWithin(m.departure_geog,
                             CAST(ST_SetSRID(ST_MakePoint(:depLng, :depLat), 4326) AS geography),
                             :radius)
              AND ST_DWithin(m.destination_geog,
                             CAST(ST_SetSRID(ST_MakePoint(:destLng, :destLat), 4326) AS geography),
                             :radius)
            ORDER BY ST_Distance(m.departure_geog,
                                 CAST(ST_SetSRID(ST_MakePoint(:depLng, :depLat), 4326) AS geography))
                   + ST_Distance(m.destination_geog,
                                 CAST(ST_SetSRID(ST_MakePoint(:destLng, :destLat), 4326) AS geography))
            limit 5
            """, nativeQuery = true)
    List<NearbyPartySummary> findNearbyParties(@Param("depLat") double depLat,
                                               @Param("depLng") double depLng,
                                               @Param("destLat") double destLat,
                                               @Param("destLng") double destLng,
                                               @Param("radius") int radius);
}