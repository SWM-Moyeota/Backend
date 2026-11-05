package team.codingforest.moyeota.matching.party.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.PartySummary;
import team.codingforest.moyeota.matching.party.domain.ReservationSummary;
import team.codingforest.moyeota.matching.party.domain.RoomType;
import team.codingforest.moyeota.matching.party.dto.NearbyPartySummary;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PartyJpaRepository extends JpaRepository<PartyEntity, Long> {
    @Query("select p from PartyEntity p left join fetch p.members where p.status = :status and p.roomType = :roomType")
    List<PartyEntity> findAllByStatusAndRoomType(@Param("status") PartyStatus status, @Param("roomType") RoomType roomType);

    @Query("select count(m) > 0 from PartyMemberEntity m where m.memberId = :memberId and m.party.status in :statuses")
    boolean existsByMemberIdAndStatusIn(@Param("memberId") Long memberId, @Param("statuses") Collection<PartyStatus> statuses);

    @Query("select p from PartyEntity p left join fetch p.members where p.id = :id")
    Optional<PartyEntity> findWithMembersById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PartyEntity p where p.id = :id")
    Optional<PartyEntity> findByForUpdate(@Param("id") Long id);

    // 지도 목록은 인원수만 필요하다 - 멤버를 join fetch 하면 방 하나가 멤버 수만큼 행으로 불어나고 route(TEXT)까지 행마다 딸려 온다
    @Query("""
            select p.id, p.departure, p.destination,
                   (select count(m) from PartyMemberEntity m where m.party = p),
                   p.capacity, p.status, p.departureLat, p.departureLng
            from PartyEntity p
            where p.status = :status
                and p.roomType = team.codingforest.moyeota.matching.party.domain.RoomType.REALTIME
                and p.departureLat between :swLat and :neLat
                and p.departureLng between :swLng and :neLng
            order by p.createdAt desc, p.id desc
            """)
    List<PartySummary> findSummariesWithinBounds(@Param("status") PartyStatus status, @Param("swLat") double swLat, @Param("neLat") double neLat,
                                                 @Param("swLng") double swLng, @Param("neLng") double neLng, Limit limit);

    // 노쇼(무효로 돌린 노쇼는 빼고)는 타지 않은 사람이라 세지 않는다
    @Query("""
                    select m.memberId as memberId, count(m) as count
                    from PartyMemberEntity m
                    where m.memberId in :memberIds and m.party.status = :status
                        and (m.noShowAt is null or m.noShowVoidedAt is not null)
                    group by m.memberId
            """)
    List<MemberRideCount> countByMemberIdsAndStatus(@Param("memberIds") Collection<Long> memberIds, @Param("status") PartyStatus status);

    // 자동 정리 대상 - 실시간 방은 만든 시각(created_at), 예약 방은 출발 시각(departure_at)이 cutoff 보다 이른 열린 방.
    // 실시간 방은 약속을 확정해 departure_at 이 있어도 만든 시각으로 가른다
    @Query("""
            select p.id from PartyEntity p
            where p.status in :statuses
                and ((p.roomType = team.codingforest.moyeota.matching.party.domain.RoomType.REALTIME and p.createdAt < :cutoff)
                    or (p.roomType = team.codingforest.moyeota.matching.party.domain.RoomType.RESERVED and p.departureAt < :cutoff))
            """)
    List<Long> findAutoCloseTargetIds(@Param("statuses") Collection<PartyStatus> statuses, @Param("cutoff") Instant cutoff);

    @Query(value = "SELECT CAST(pg_advisory_xact_lock(:memberId) AS text)", nativeQuery = true)
    String lockMember(@Param("memberId") Long memberId);

    @Query("select count(m) > 0 from PartyMemberEntity m where m.memberId = :memberId and m.party.roomType = :roomType and m.party.status in :statuses")
    boolean existsByMemberIdAndRoomTypeAndStatusIn(@Param("memberId") Long memberId, @Param("roomType") RoomType roomType, @Param("statuses") Collection<PartyStatus> statuses);

    @Query("""
            select count(m) > 0 from PartyMemberEntity m
            where m.memberId = :memberId
                and m.party.roomType = team.codingforest.moyeota.matching.party.domain.RoomType.RESERVED
                and m.party.status in :statuses
                and m.party.departureAt between :from and :to
            """)
    boolean existsReservationBetween(@Param("memberId") Long memberId, @Param("statuses") Collection<PartyStatus> statuses, @Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select p.id, p.departure, p.destination,
                   (select count(m) from PartyMemberEntity m where m.party = p),
                   p.capacity, p.status, p.departureLat, p.departureLng, p.destinationLat, p.destinationLng, p.departureAt
            from PartyEntity p
            where p.status = :status
                and p.roomType = team.codingforest.moyeota.matching.party.domain.RoomType.RESERVED
                and p.departureAt between :from and :to
            order by p.departureAt asc, p.id asc
            """)
    List<ReservationSummary> findReservations(@Param("status") PartyStatus status, @Param("from") Instant from, @Param("to") Instant to, Limit limit);

    @Query("""
            select p.id, p.departure, p.destination,
                   (select count(m) from PartyMemberEntity m where m.party = p),
                   p.capacity, p.status, p.departureLat, p.departureLng, p.destinationLat, p.destinationLng, p.departureAt
            from PartyEntity p
            where p.status = :status
                and p.roomType = team.codingforest.moyeota.matching.party.domain.RoomType.RESERVED
                and p.departureAt between :from and :to
                and p.departureLat between :swLat and :neLat
                and p.departureLng between :swLng and :neLng
            order by p.departureAt asc, p.id asc
            """)
    List<ReservationSummary> findReservationsWithinBounds(@Param("status") PartyStatus status, @Param("from") Instant from, @Param("to") Instant to,
                                                          @Param("swLat") double swLat, @Param("neLat") double neLat,
                                                          @Param("swLng") double swLng, @Param("neLng") double neLng, Limit limit);

    @Query("""
            select p.id from PartyEntity p
            where p.status = :status
                and ((p.roomType = team.codingforest.moyeota.matching.party.domain.RoomType.REALTIME and p.createdAt < :realtimeCreatedBefore)
                    or (p.roomType = team.codingforest.moyeota.matching.party.domain.RoomType.RESERVED and p.departureAt <= :now))
            """)
    List<Long> findRecruitingExpiredIds(@Param("status") PartyStatus status, @Param("realtimeCreatedBefore") Instant realtimeCreatedBefore, @Param("now") Instant now);

    @Query("""
            select p.id from PartyEntity p
            where p.status in :statuses
                and p.roomType = team.codingforest.moyeota.matching.party.domain.RoomType.RESERVED
                and p.reminderSentAt is null
                and p.departureAt between :from and :to
            """)
    List<Long> findIdsToRemind(@Param("statuses") Collection<PartyStatus> statuses, @Param("from") Instant from, @Param("to") Instant to);

    // 약속 시각 알림·출발 재촉 대상(1분마다). 상태와 departure_at IS NOT NULL 을 리터럴로 써야 부분 인덱스
    // idx_match_room_open_confirmed_departure(V28)의 조건과 맞아 열린 확정 방만 훑는다 - 파라미터로 넘기면 일반 계획에서 인덱스 조건을 증명하지 못한다
    @Query("""
            select p.id from PartyEntity p
            where p.status in (team.codingforest.moyeota.matching.party.domain.PartyStatus.ACTIVE,
                               team.codingforest.moyeota.matching.party.domain.PartyStatus.COMPLETED)
                and p.departureAt is not null
                and p.departureAt <= :now
                and p.meetingNotifiedAt is null
            """)
    List<Long> findMeetingNoticeTargetIds(@Param("now") Instant now);

    // 처리한 차례 수(departure_reminder_count)마다 기준 시각이 다르다 - dueN = now - Party.DEPARTURE_REMINDER_OFFSETS[N].
    // 차례를 다 쓴 방(4)은 어느 조건에도 걸리지 않는다
    @Query("""
            select p.id from PartyEntity p
            where p.status in (team.codingforest.moyeota.matching.party.domain.PartyStatus.ACTIVE,
                               team.codingforest.moyeota.matching.party.domain.PartyStatus.COMPLETED)
                and p.departureAt is not null
                and p.departureAt <= :due0
                and ((p.departureReminderCount = 0 and p.departureAt <= :due0)
                    or (p.departureReminderCount = 1 and p.departureAt <= :due1)
                    or (p.departureReminderCount = 2 and p.departureAt <= :due2)
                    or (p.departureReminderCount = 3 and p.departureAt <= :due3))
            """)
    List<Long> findDepartureReminderTargetIds(@Param("due0") Instant due0, @Param("due1") Instant due1,
                                              @Param("due2") Instant due2, @Param("due3") Instant due3);

    @Query(value = """
            SELECT m.id, m.departure_lat, m.departure_lng, m.destination_lat, m.destination_lng, m.departure_place, m.destination_place, m.capacity,
                   CAST((SELECT COUNT(*) FROM user_match_room um WHERE um.match_id = m.id) AS integer) AS current_members
            FROM match_room m
            WHERE m.status = 'ACTIVE'
              AND m.room_type = 'REALTIME'
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

    // 출발지 도착 판정. geography 라 타원체 위 m 단위로 재고, ST_DWithin 은 경계(radius 와 같은 거리)를 포함한다.
    // ST_MakePoint 는 경도가 먼저다. EXISTS 로 감싸 없는 방이어도 행 하나(false)가 돌아온다
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM match_room m
                WHERE m.id = :partyId
                  AND ST_DWithin(m.departure_geog,
                                 CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography),
                                 :radius)
            )
            """, nativeQuery = true)
    boolean isNearDeparture(@Param("partyId") Long partyId,
                            @Param("lat") double latitude,
                            @Param("lng") double longitude,
                            @Param("radius") double radiusMeters);
}
