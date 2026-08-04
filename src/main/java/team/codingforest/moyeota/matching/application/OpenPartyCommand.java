package team.codingforest.moyeota.matching.application;

public record OpenPartyCommand(
        long hostId,
        double departureLat, double departureLng,
        double destinationLat, double destinationLng,
        String departure, String destination,
        int capacity, int departureRadius, int destinationRadius) {
}
