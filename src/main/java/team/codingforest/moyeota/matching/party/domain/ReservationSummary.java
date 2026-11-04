package team.codingforest.moyeota.matching.party.domain;

import java.time.Instant;

public record ReservationSummary(Long id, String departure, String destination, Long currentMembers, Integer capacity, PartyStatus status,
                                 Double departureLat, Double departureLng, Double destinationLat, Double destinationLng, Instant departureAt) {
}
