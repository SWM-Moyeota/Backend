package team.codingforest.moyeota.place.search.dto;

import team.codingforest.moyeota.place.search.domain.Place;

public record PlaceSearchResponse(String name, String roadName, Double latitude, Double longitude) {

    public static PlaceSearchResponse toDto(Place place) {
        return new PlaceSearchResponse(place.name(), place.roadName(), place.latitude(), place.longitude());
    }
}
