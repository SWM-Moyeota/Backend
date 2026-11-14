package team.codingforest.moyeota.matching.party.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record OpenReservationRequest(
		@NotNull(message = "출발지 위도는 필수입니다") @Schema(example = "37.4979") Double departureLat,
		@NotNull(message = "출발지 경도는 필수입니다") @Schema(example = "127.0276") Double departureLng,
		@NotNull(message = "도착지 위도는 필수입니다") @Schema(example = "37.3948") Double destinationLat,
		@NotNull(message = "도착지 경도는 필수입니다") @Schema(example = "127.1112") Double destinationLng,

		@NotBlank(message = "출발지 이름은 필수입니다") @Schema(example = "강남역") String departure,
		@NotBlank(message = "도착지 이름은 필수입니다") @Schema(example = "판교역") String destination,

		@NotNull(message = "정원은 필수입니다")
		@Min(value = 2, message = "예약 방의 정원은 2명 이상입니다")
		@Max(value = 4, message = "정원은 4명 이하입니다")
		@Schema(description = "정원(생성자 포함) 2~4", example = "3") Integer capacity,

		@NotNull(message = "출발 시각은 필수입니다")
		@Schema(description = "출발 시각. 지금부터 7일 이내", example = "2026-11-03T08:00:00Z") Instant departureAt) {

	public OpenReservationCommand toCommand(Long creatorMemberId) {
		return new OpenReservationCommand(creatorMemberId, departureLat, departureLng, destinationLat, destinationLng,
				departure, destination, capacity, departureAt);
	}
}
