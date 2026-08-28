package team.codingforest.moyeota.place.application.dto;

import team.codingforest.moyeota.place.domain.Place;

public record PlaceSearchResponse(String name, String roadName, Double latitude, Double longitude) {

    public static PlaceSearchResponse toDto(Place place) {
        return new PlaceSearchResponse(place.name(), place.roadName(), place.latitude(), place.longitude());
    }
}
