package team.codingforest.moyeota.matching.party.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OpenPartyRequest(
		@Schema(description = "무시됨 - 생성자는 토큰에서", deprecated = true) Long creatorId,

		@NotNull(message = "출발지 위도는 필수입니다") @Schema(example = "37.4979") Double departureLat,
		@NotNull(message = "출발지 경도는 필수입니다") @Schema(example = "127.0276") Double departureLng,
		@NotNull(message = "도착지 위도는 필수입니다") @Schema(example = "37.3948") Double destinationLat,
		@NotNull(message = "도착지 경도는 필수입니다") @Schema(example = "127.1112") Double destinationLng,

		@NotBlank(message = "출발지 이름은 필수입니다") @Schema(example = "강남역") String departure,
		@NotBlank(message = "도착지 이름은 필수입니다") @Schema(example = "판교역") String destination,

		@NotNull(message = "정원은 필수입니다")
		@Min(value = 1, message = "정원은 1명 이상입니다")
		@Max(value = 3, message = "정원은 3명 이하입니다")
		@Schema(description = "정원(생성자 포함) 1~3", example = "3") Integer capacity) {

	/** creatorId는 토큰에서 온다. 본문의 creatorId 필드는 구버전 호환용으로 받기만 하고 무시한다 */
	public OpenPartyCommand toCommand(Long creatorMemberId) {
		return new OpenPartyCommand(creatorMemberId, departureLat, departureLng, destinationLat, destinationLng,
				departure, destination, capacity);
	}
}
