package team.codingforest.moyeota.place.favorite.dto;

import team.codingforest.moyeota.place.favorite.domain.FavoritePlace;

public record FavoritePlaceResponse(String placeName, String roadName, Double latitude, Double longitude, Integer placeSequence) {

    public static FavoritePlaceResponse toDto(FavoritePlace place) {
        return new FavoritePlaceResponse(place.getPlaceName(), place.getRoadName(), place.getLatitude(), place.getLongitude(), place.getPlaceSequence());
    }
}
