package team.codingforest.moyeota.place.application.dto;

public record FavoritePlaceRequest(String placeName, String roadName, Double latitude, Double longitude) {

    public static FavoritePlaceCommand toCommand(FavoritePlaceRequest req) {
        return new FavoritePlaceCommand(req.placeName, req.roadName, req.latitude, req.longitude);
    }
}
