package team.codingforest.moyeota.matching.application.dto;

public record OpenPartyRequest(Long creatorId, Double departureLat, Double departureLng,
                               Double destinationLat, Double destinationLng, String departure, String destination,
                               Integer capacity, Integer departureRadius, Integer destinationRadius) {

    public OpenPartyCommand toCommand() {
        return new OpenPartyCommand(creatorId, departureLat, departureLng, destinationLat, destinationLng,
                                departure, destination, capacity, departureRadius, destinationRadius);
    }
}
