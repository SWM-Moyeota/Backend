package team.codingforest.moyeota.matching.application.dto;

import java.time.Instant;

public record OpenPartyResponse(Long id, Long hostId, Double departureLat, Double departureLng,
                                Double destinationLat, Double destinationLng, String departure, String destination,
                                Integer capacity, Integer currentMembers, Integer departureRadius, Integer destinationRadius,
                                String status, Instant createdAt, Integer estimateFare, Integer estimateTime, String route,
                                Long taxiDriverId) {

    public static OpenPartyResponse from(PartyResult party) {
        return new OpenPartyResponse(party.id(), party.hostId(), party.departureLat(), party.departureLng(),
                                    party.destinationLat(), party.destinationLng(), party.departure(), party.destination(),
                                    party.capacity(), party.currentMembers(), party.departureRadius(), party.destinationRadius(),
                                    party.status(), party.createdAt(), party.estimateFare(), party.estimateTime(), party.route(), party.taxiDriverId());
    }
}
