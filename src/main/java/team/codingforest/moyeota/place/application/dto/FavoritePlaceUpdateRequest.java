package team.codingforest.moyeota.place.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record FavoritePlaceUpdateRequest(@Schema(description = "바꿀 이름. 비우면 이름은 유지", example = "본가", nullable = true) String placeName,
                                         @Schema(example = "서울 강남구 테헤란로 152") String roadName,
                                         @Schema(example = "37.4979") Double latitude, @Schema(example = "127.0276") Double longitude) {

    public static FavoritePlaceUpdateCommand toCommand(FavoritePlaceUpdateRequest req) {
        return new FavoritePlaceUpdateCommand(req.placeName, req.roadName, req.latitude, req.longitude);
    }
}
