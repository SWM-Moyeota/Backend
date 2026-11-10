package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.matching.api.dto.PartyPlaces;
import team.codingforest.moyeota.matching.party.dto.NearbyPartySummary;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface Parties {
    Optional<Party> findById(Long id);
    Party save(Party party);
    boolean existsOngoingByMemberId(Long memberId);
    Optional<Party> findByIdForUpdate(Long id);
    List<PartySummary> findSummariesWithinBounds(PartyStatus status, double swLat, double neLat, double swLng, double neLng, int limit);
    Map<Long, Integer> countFinishedRides(List<Long> memberIds);
    List<Long> findAutoCloseTargets(Instant cutoff);
    List<NearbyPartySummary> findMatchesWithinRadius(double departLat, double departLng, double destLat, double destLng, int radius);
    boolean isNearDeparture(Long partyId, double latitude, double longitude, double radiusMeters);
    void lockMember(Long memberId);
    boolean existsOngoingRealtimeByMemberId(Long memberId);
    boolean existsOngoingReservationBetween(Long memberId, Instant from, Instant to);
    List<ReservationSummary> findReservationsWithinBounds(Instant from, Instant to, double swLat, double neLat, double swLng, double neLng, int limit);
    List<NearbyReservation> findReservationsWithinRadius(Instant from, Instant to, Location departure, Location destination, int radius, int limit);
	List<Long> findOpenConfirmedIdsDepartingBetween(Instant from, Instant to);
	List<Long> findOpenReservationIdsDepartingBetween(Instant from, Instant to);
    Map<Long, PartyPlaces> findPlaces(Collection<Long> ids);
}
