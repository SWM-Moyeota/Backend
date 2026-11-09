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

    // 지도 목록 조회 쿼리
    @Query("""
            select p.id, p.departure, p.destination,
                   (select count(m) from PartyMemberEntity m where m.party = p),
                   p.capacity, p.status, p.departureLat, p.departureLng
            from PartyEntity p
            where p.status = :status
                and p.roomType = team.codingforest.moyeota.matching.party.domain.RoomType.REALTIME
                and (select count(m) from PartyMemberEntity m where m.party = p) < p.capacity
                and p.departureLat between :swLat and :neLat
                and p.departureLng between :swLng and :neLng
            order by p.createdAt desc, p.id desc
            """)
    List<PartySummary> findSummariesWithinBounds(@Param("status") PartyStatus status, @Param("swLat") double swLat, @Param("neLat") double neLat,
                                                 @Param("swLng") double swLng, @Param("neLng") double neLng, Limit limit);

    // 동승 횟수 - 끝난(FINISHED) 방에 있었던 횟수
    @Query("""
                    select m.memberId as memberId, count(m) as count
                    from PartyMemberEntity m
                    where m.memberId in :memberIds and m.party.status = :status
                    group by m.memberId
            """)
    List<MemberRideCount> countByMemberIdsAndStatus(@Param("memberIds") Collection<Long> memberIds, @Param("status") PartyStatus status);

    // 자동 정리 대상 - 약속이 있는 방(확정한 실시간 방·예약 방)은 약속 시각, 약속이 없는 실시간 방은 만든 시각이 cutoff 보다 이른 열린 방
    @Query("""
            select p.id from PartyEntity p
            where p.status in :statuses
                and ((p.departureAt is null and p.createdAt < :cutoff)
                    or (p.departureAt is not null and p.departureAt < :cutoff))
            """)
    List<Long> findAutoCloseTargetIds(@Param("statuses") Collection<PartyStatus> statuses, @Param("cutoff") Instant cutoff);

	// 한명의 사용자가 동시 요청을 했을떄 정합성을 맞추기 위한 advisory_lock 키 락
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
                and (select count(m) from PartyMemberEntity m where m.party = p) < p.capacity
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
                and (select count(m) from PartyMemberEntity m where m.party = p) < p.capacity
                and p.departureAt between :from and :to
                and p.departureLat between :swLat and :neLat
                and p.departureLng between :swLng and :neLng
            order by p.departureAt asc, p.id asc
            """)
    List<ReservationSummary> findReservationsWithinBounds(@Param("status") PartyStatus status, @Param("from") Instant from, @Param("to") Instant to,
                                                          @Param("swLat") double swLat, @Param("neLat") double neLat,
                                                          @Param("swLng") double swLng, @Param("neLng") double neLng, Limit limit);

    // 예약 방 검색 2단계 - 1단계(native)가 고른 id 의 요약을 예약 목록과 같은 select 로 읽는다. 순서는 부르는 쪽이 1단계 순서로 되돌린다
    @Query("""
            select p.id, p.departure, p.destination,
                   (select count(m) from PartyMemberEntity m where m.party = p),
                   p.capacity, p.status, p.departureLat, p.departureLng, p.destinationLat, p.destinationLng, p.departureAt
            from PartyEntity p
            where p.id in :ids
            """)
    List<ReservationSummary> findReservationSummariesByIds(@Param("ids") Collection<Long> ids);

    @Query("select p.id, p.departure, p.destination from PartyEntity p where p.id in :ids")
    List<PartyPlaceRow> findPlacesByIds(@Param("ids") Collection<Long> ids);

    @Query("""
            select p.id from PartyEntity p
            where p.status = :status
                and (select count(m) from PartyMemberEntity m where m.party = p) < 2
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

    // reminder 메시지를 보낼때 시간별로 지정 -> 30분 1시간 ~~
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
              AND (SELECT COUNT(*) FROM user_match_room um WHERE um.match_id = m.id) < m.capacity
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

    // 예약 방 검색 1단계(도착지 있음) - 고른 방의 id 와 도착지 근처 여부만 읽는다. 요약은 2단계(findReservationSummariesByIds)가 읽는다.
    // 출발 시각은 [from, to) - to 는 다음 날 00:00(KST)라 뺀다. 상태·방 종류를 리터럴로 써야 부분 인덱스 조건과 맞는다
    @Query(value = """
            SELECT m.id,
                   ST_DWithin(m.destination_geog,
                              CAST(ST_SetSRID(ST_MakePoint(:destLng, :destLat), 4326) AS geography),
                              :radius) AS destination_nearby
            FROM match_room m
            WHERE m.status = 'ACTIVE'
              AND m.room_type = 'RESERVED'
              AND (SELECT COUNT(*) FROM user_match_room um WHERE um.match_id = m.id) < m.capacity
              AND m.departure_at >= :from
              AND m.departure_at < :to
              AND ST_DWithin(m.departure_geog,
                             CAST(ST_SetSRID(ST_MakePoint(:depLng, :depLat), 4326) AS geography),
                             :radius)
            ORDER BY destination_nearby DESC, m.departure_at ASC, m.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<ReservationSearchRow> findReservationIdsNearRoute(@Param("depLat") double depLat,
                                                           @Param("depLng") double depLng,
                                                           @Param("destLat") double destLat,
                                                           @Param("destLng") double destLng,
                                                           @Param("from") Instant from,
                                                           @Param("to") Instant to,
                                                           @Param("radius") int radius,
                                                           @Param("limit") int limit);

    // 예약 방 검색 1단계(도착지 없음). 조건은 위와 같고 출발 시각 순이다
    @Query(value = """
            SELECT m.id
            FROM match_room m
            WHERE m.status = 'ACTIVE'
              AND m.room_type = 'RESERVED'
              AND (SELECT COUNT(*) FROM user_match_room um WHERE um.match_id = m.id) < m.capacity
              AND m.departure_at >= :from
              AND m.departure_at < :to
              AND ST_DWithin(m.departure_geog,
                             CAST(ST_SetSRID(ST_MakePoint(:depLng, :depLat), 4326) AS geography),
                             :radius)
            ORDER BY m.departure_at ASC, m.id ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findReservationIdsNearDeparture(@Param("depLat") double depLat,
                                               @Param("depLng") double depLng,
                                               @Param("from") Instant from,
                                               @Param("to") Instant to,
                                               @Param("radius") int radius,
                                               @Param("limit") int limit);

    // 출발지 도착 판정. 인자로 들어오는 반경을 기준으로 안에 있는지 판정
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
