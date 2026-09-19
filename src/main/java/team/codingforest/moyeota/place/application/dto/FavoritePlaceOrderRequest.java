package team.codingforest.moyeota.place.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record FavoritePlaceOrderRequest(@Schema(description = "원하는 순서대로 나열한 장소 이름 전체", example = "[\"회사\", \"우리집\", \"헬스장\"]") List<String> placeNames) {
}
