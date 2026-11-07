package team.codingforest.moyeota.matching.party.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record ConfirmPartyRequest(
		@NotNull(message = "약속 시간은 필수입니다")
		@Schema(description = "약속 시간(ISO-8601). 확정하는 지금부터 1시간 안. 장소는 방의 출발지", example = "2026-11-03T08:30:00Z") Instant departureAt) {
}
