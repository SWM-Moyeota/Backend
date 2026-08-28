package team.codingforest.moyeota.matching.api;

public record PartySummary(Long id, double departureLatitude, double departureLongitude, double destinationLatitude, double destinationLongitude, String departure, String destination, int memberCount, Integer estimatedFare, Integer estimatedTime) {
}
