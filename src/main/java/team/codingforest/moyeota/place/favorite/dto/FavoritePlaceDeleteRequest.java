package team.codingforest.moyeota.place.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record FavoritePlaceDeleteRequest(
        @Schema(description = "삭제할 장소 ID 목록 (1~100개)", example = "[123, 456]")
        List<Long> placeIds
) {}
