package team.codingforest.moyeota.matching.domain;

public record RouteKey(double departureLat, double departureLng, double destinationLat, double destinationLng) {
    public static RouteKey of(double departureLat, double departureLng, double destinationLat, double destinationLng) {
        return new RouteKey(round(departureLat), round(departureLng), round(destinationLat), round(destinationLng));
    }

    private static double round(double v) {
        return Math.round(v * 100_000) / 100_000.0;
    }

    public String value() {
        return departureLat + "," + departureLng + ":" + destinationLat + "," + destinationLng;
    }
}
