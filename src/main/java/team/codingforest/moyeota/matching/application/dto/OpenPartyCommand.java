package team.codingforest.moyeota.matching.application.dto;

public record OpenPartyCommand(
        long hostId,
        double departureLat, double departureLng,
        double destinationLat, double destinationLng,
        String departure, String destination,
        int capacity, int departureRadius, int destinationRadius) {

    public OpenPartyCommand toCommand() {
        return new OpenPartyCommand(hostId, departureLat, departureLng,
                destinationLat, destinationLng, departure, destination,
                capacity, departureRadius, destinationRadius);
    }
}
