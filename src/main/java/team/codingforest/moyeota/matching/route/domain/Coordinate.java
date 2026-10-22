package team.codingforest.moyeota.matching.route.domain;

public record Coordinate(double lat, double lng) {
    public static Coordinate from(double lat, double lng) {
        return new Coordinate(lat, lng);
    }
}
