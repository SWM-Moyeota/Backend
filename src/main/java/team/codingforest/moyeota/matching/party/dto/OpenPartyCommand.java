package team.codingforest.moyeota.matching.party.dto;

public record OpenPartyCommand(
        long creatorId,
        double departureLat, double departureLng,
        double destinationLat, double destinationLng,
        String departure, String destination,
        int capacity, int departureRadius, int destinationRadius) {
}
