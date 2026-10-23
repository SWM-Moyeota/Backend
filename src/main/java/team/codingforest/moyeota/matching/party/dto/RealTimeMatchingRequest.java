package team.codingforest.moyeota.matching.party.dto;

public record RealTimeMatchingRequest(double departureLat, double departureLng, double destLat, double destLng) {
    public RealTimeMatchingCommand toCommand() {
        return RealTimeMatchingCommand.from(departureLat, departureLng, destLat, destLng);
    }
}
