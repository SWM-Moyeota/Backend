package team.codingforest.moyeota.matching.party.dto;

import team.codingforest.moyeota.matching.party.domain.NearbyReservation;
import team.codingforest.moyeota.matching.party.domain.ReservationSummary;

import java.time.Instant;
import java.util.List;

/**
 *  예약 방 검색 결과. 항목은 예약 방 목록(ReservationListResponse)과 같은 값에 destinationNearby 가 붙는다
 */
public record ReservationSearchResponse(List<ReservationSearchItem> list) {

    public record ReservationSearchItem(Long partyId, String departure, String destination, Integer currentMembers, Integer capacity, String status,
                                        Double departureLat, Double departureLng, Double destinationLat, Double destinationLng, Instant departureAt,
                                        Boolean destinationNearby) {
    }

    public static ReservationSearchResponse from(List<NearbyReservation> results) {
        return new ReservationSearchResponse(results.stream()
                .map(r -> {
                    ReservationSummary s = r.reservation();
                    return new ReservationSearchItem(s.id(), s.departure(), s.destination(), s.currentMembers().intValue(), s.capacity(), s.status().name(),
                            s.departureLat(), s.departureLng(), s.destinationLat(), s.destinationLng(), s.departureAt(), r.destinationNearby());
                })
                .toList());
    }
}
