package team.codingforest.moyeota.matching.party.dto;

public record RealTimeMatchingCommand(double departureLat, double departureLng, double destLat, double destLng) {
    public static RealTimeMatchingCommand from(double departureLat, double departureLng, double destLat, double destLng) {
        return new RealTimeMatchingCommand(departureLat, departureLng, destLat, destLng);
    }
}
