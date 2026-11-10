package team.codingforest.moyeota.matching.party.dto;

import java.time.Instant;

public record OpenPartyResponse(Long id, Double departureLat, Double departureLng,
                                Double destinationLat, Double destinationLng, String departure, String destination,
                                Integer capacity, Integer currentMembers,
                                String status, Instant createdAt, Integer estimateFare, Integer estimateTime, String route,
                                String roomType, Instant departureAt) {

    public static OpenPartyResponse from(PartyResult party) {
        return new OpenPartyResponse(party.id(), party.departureLat(), party.departureLng(),
                                    party.destinationLat(), party.destinationLng(), party.departure(), party.destination(),
                                    party.capacity(), party.currentMembers(),
                                    party.status(), party.createdAt(), party.estimateFare(), party.estimateTime(), party.route(),
                                    party.roomType(), party.departureAt());
    }
}
