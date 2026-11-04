package team.codingforest.moyeota.matching.party.dto;

import team.codingforest.moyeota.matching.party.domain.ReservationSummary;

import java.time.Instant;
import java.util.List;

public record ReservationListResponse(List<ReservationItem> list) {

    public record ReservationItem(Long partyId, String departure, String destination, Integer currentMembers, Integer capacity, String status,
                                  Double departureLat, Double departureLng, Double destinationLat, Double destinationLng, Instant departureAt) {
    }

    public static ReservationListResponse from(List<ReservationSummary> summaries) {
        return new ReservationListResponse(summaries.stream()
                .map(s -> new ReservationItem(s.id(), s.departure(), s.destination(), s.currentMembers().intValue(), s.capacity(), s.status().name(),
                        s.departureLat(), s.departureLng(), s.destinationLat(), s.destinationLng(), s.departureAt()))
                .toList());
    }
}
