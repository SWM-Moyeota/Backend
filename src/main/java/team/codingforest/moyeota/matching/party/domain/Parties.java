package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.matching.api.dto.MatchingTarget;
import team.codingforest.moyeota.matching.party.dto.NearbyPartySummary;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface Parties {
    Optional<Party> findById(Long id);
    Party save(Party party);
    List<Party> findRealtimeByStatus(PartyStatus status);
    boolean existsOngoingByMemberId(Long memberId);
    Optional<Party> findByIdForUpdate(Long id);
    List<MatchingTarget> findMatchingTargets();
    boolean hasOngoingRide(Long driverId);
    List<PartySummary> findSummariesWithinBounds(PartyStatus status, double swLat, double neLat, double swLng, double neLng, int limit);
    Map<Long, Integer> countFinishedRides(List<Long> memberIds);
    /**
     *  자동 정리 대상 - 열린 방(ACTIVE·COMPLETED) 중 실시간 방은 만든 시각, 예약 방은 출발 시각이 cutoff 보다 이른 방.
     *  약속을 확정한 실시간 방도 약속 시간이 아니라 만든 시각으로 가른다
     */
    List<Long> findAutoCloseTargets(Instant cutoff);
    List<NearbyPartySummary> findMatchesWithinRadius(double departLat, double departLng, double destLat, double destLng, int radius);
    // 이 방의 출발지에서 radiusMeters 안(경계 포함)인지 - 거리는 DB(PostGIS)가 잰다. 없는 방이면 false
    boolean isNearDeparture(Long partyId, double latitude, double longitude, double radiusMeters);
    boolean existsById(Long id);
    boolean hasMember(Long partyId, Long memberId);
    void lockMember(Long memberId);
    boolean existsOngoingRealtimeByMemberId(Long memberId);
    boolean existsOngoingReservationBetween(Long memberId, Instant from, Instant to);
    List<ReservationSummary> findReservations(Instant from, Instant to, int limit);
    List<ReservationSummary> findReservationsWithinBounds(Instant from, Instant to, double swLat, double neLat, double swLng, double neLng, int limit);
    List<Long> findRecruitingExpired(Instant realtimeCreatedBefore, Instant now);
    List<Long> findReservationsToRemind(Instant from, Instant to);
}
