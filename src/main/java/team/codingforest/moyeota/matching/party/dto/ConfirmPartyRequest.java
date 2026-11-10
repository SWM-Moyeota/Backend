package team.codingforest.moyeota.matching.party.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.MeetingPlan;

import java.time.Instant;

/**
 *  약속 확정 요청 - 약속 시간과 만남 장소를 함께 확정한다. 장소를 바꾸지 않아도 방의 지금 출발지를 그대로 보낸다
 */
public record ConfirmPartyRequest(
		@NotNull(message = "약속 시간은 필수입니다")
		@Schema(description = "약속 시간(ISO-8601). 확정하는 지금부터 5분 뒤에서 1시간 사이", example = "2026-11-03T08:30:00Z") Instant departureAt,
		@NotNull(message = "만남 장소 위도는 필수입니다")
		@Schema(description = "만남 장소 위도. 바꾸지 않으면 방의 지금 출발지 위도", example = "37.4985") Double departureLat,
		@NotNull(message = "만남 장소 경도는 필수입니다")
		@Schema(description = "만남 장소 경도. 바꾸지 않으면 방의 지금 출발지 경도", example = "127.0290") Double departureLng,
		@NotBlank(message = "만남 장소 이름은 필수입니다")
		@Size(max = 100, message = "만남 장소 이름은 100자 이하여야 합니다")
		@Schema(description = "만남 장소 이름. 채팅으로 정한 그대로, 바꾸지 않으면 방의 지금 출발지 이름", example = "강남역 2번 출구") String departure) {

	public MeetingPlan toPlan() {
		return new MeetingPlan(departureAt, new Location(departureLat, departureLng), departure);
	}
}
