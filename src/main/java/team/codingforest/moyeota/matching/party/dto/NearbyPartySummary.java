package team.codingforest.moyeota.matching.party.dto;

public record NearbyPartySummary(Long id, Double departureLat, Double departureLng, Double destinationLat, Double destinationLng, String departure, String destination, Integer capacity, Integer currentMembers) {
}
