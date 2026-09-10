package team.codingforest.moyeota.place.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
public record FavoritePlaceRequest(@Schema(example = "우리집") String placeName, @Schema(example = "서울 강남구 테헤란로 152") String roadName,
                                   @Schema(example = "37.4979") Double latitude, @Schema(example = "127.0276") Double longitude) {

    public static FavoritePlaceCommand toCommand(FavoritePlaceRequest req) {
        return new FavoritePlaceCommand(req.placeName, req.roadName, req.latitude, req.longitude);
    }
}
