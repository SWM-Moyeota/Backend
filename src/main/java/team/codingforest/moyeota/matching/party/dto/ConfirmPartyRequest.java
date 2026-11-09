package team.codingforest.moyeota.matching.party.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.MeetingPlan;

import java.time.Instant;

/**
 *  약속 확정 요청. 만남 장소 셋(departureLat·departureLng·departure)은 함께 보내거나 함께 비운다.
 *  11-03 출시 앱은 departureAt 만 보낸다 - 그러면 출발지는 그대로다
 */
public record ConfirmPartyRequest(
        @NotNull(message = "약속 시간은 필수입니다")
        @Schema(description = "약속 시간(ISO-8601). 확정하는 지금부터 5분 뒤에서 1시간 사이", example = "2026-11-03T08:30:00Z") Instant departureAt,
        @Schema(description = "만남 장소 위도(선택). 보내면 방의 출발지가 이 자리로 바뀐다", example = "37.4985", nullable = true) Double departureLat,
        @Schema(description = "만남 장소 경도(선택)", example = "127.0290", nullable = true) Double departureLng,
        @Size(max = 100, message = "만남 장소 이름은 100자 이하여야 합니다")
        @Schema(description = "만남 장소 이름(선택). 채팅으로 정한 그대로", example = "강남역 2번 출구", nullable = true) String departure) {

    public MeetingPlan toPlan() {
        if(departureLat == null && departureLng == null) return new MeetingPlan(departureAt, null, departure);

        if(departureLat == null || departureLng == null) throw new BusinessException(MatchingErrorCode.INCOMPLETE_MEETING_PLACE);

        return new MeetingPlan(departureAt, new Location(departureLat, departureLng), departure);
    }
}
