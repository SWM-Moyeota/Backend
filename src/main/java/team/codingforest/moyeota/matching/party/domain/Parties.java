package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.matching.api.dto.MatchingTarget;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface Parties {
    Optional<Party> findById(Long id);
    Optional<PartyStatusSnapshot> findStatusSnapshotById(Long id);
    Party save(Party party);
    List<Party> findAllByStatus(PartyStatus status);
    boolean existsOngoingByMemberId(Long memberId);
    Optional<Party> findByIdForUpdate(Long id);
    List<MatchingTarget> findMatchingTargets();
    boolean hasOngoingRide(Long driverId);
    /** 최신순으로 최대 limit 개 */
    List<PartySummary> findSummariesWithinBounds(PartyStatus status, double swLat, double neLat, double swLng, double neLng, int limit);
    Map<Long, Integer> countFinishedRides(List<Long> memberIds);
    List<Long> findCompletedBefore(Instant before);
}
