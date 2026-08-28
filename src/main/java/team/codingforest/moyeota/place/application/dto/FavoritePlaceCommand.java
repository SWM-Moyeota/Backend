package team.codingforest.moyeota.place.application.dto;

import team.codingforest.moyeota.place.domain.FavoritePlace;

public record FavoritePlaceCommand(String placeName, String roadName, Double latitude, Double longitude) {
    public FavoritePlace toDomain(Long userId, Integer placeSequence) {
        return FavoritePlace.from(userId, placeName, roadName, latitude, longitude, placeSequence);
    }
}
