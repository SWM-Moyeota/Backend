package team.codingforest.moyeota.matching.application.dto;

public record OpenPartyRequest(Long hostId, Double departureLat, Double departureLng,
                               Double destinationLat, Double destinationLng, String departure, String destination,
                               Integer capacity, Integer departureRadius, Integer destinationRadius) {

    public OpenPartyCommand toCommand() {
        return new OpenPartyCommand(hostId, departureLat, departureLng, destinationLat, destinationLng,
                                departure, destination, capacity, departureRadius, destinationRadius);
    }
}
