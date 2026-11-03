package team.codingforest.moyeota.matching.party.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ArriveRequest(
		@NotNull(message = "위도는 필수입니다")
		@Schema(description = "지금 내 위치의 위도", example = "37.4979") Double latitude,
		@NotNull(message = "경도는 필수입니다")
		@Schema(description = "지금 내 위치의 경도", example = "127.0276") Double longitude) {
}
