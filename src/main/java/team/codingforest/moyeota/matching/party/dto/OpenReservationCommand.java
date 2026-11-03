package team.codingforest.moyeota.matching.party.dto;

import java.time.Instant;

public record OpenReservationCommand(
        long creatorId,
        Double departureLat, Double departureLng,
        Double destinationLat, Double destinationLng,
        String departure, String destination,
        Integer capacity,
        Instant departureAt) {
}
