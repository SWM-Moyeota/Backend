package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.matching.api.dto.MatchingTarget;
import team.codingforest.moyeota.matching.party.dto.NearbyPartySummary;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface Parties {
    Optional<Party> findById(Long id);
    Party save(Party party);
    List<Party> findAllByStatus(PartyStatus status);
    boolean existsOngoingByMemberId(Long memberId);
    Optional<Party> findByIdForUpdate(Long id);
    List<MatchingTarget> findMatchingTargets();
    boolean hasOngoingRide(Long driverId);
    List<PartySummary> findSummariesWithinBounds(PartyStatus status, double swLat, double neLat, double swLng, double neLng, int limit);
    Map<Long, Integer> countFinishedRides(List<Long> memberIds);
    List<Long> findCompletedBefore(Instant before);
    List<NearbyPartySummary> findMatchesWithinRadius(double departLat, double departLng, double destLat, double destLng, int radius);
    boolean existsById(Long id);
    boolean hasMember(Long partyId, Long memberId);
    List<PartyVersion> findVersions(Collection<Long> ids);
}
