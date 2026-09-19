package team.codingforest.moyeota.place.application.dto;

public record FavoritePlaceUpdateCommand(String placeName, String roadName, Double latitude, Double longitude) {

    public boolean hasNewName() {
        return placeName != null && !placeName.isBlank();
    }
}
