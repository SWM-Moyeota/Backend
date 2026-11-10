package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Instant;

/**
 *  방장이 확정하는 약속 - 약속 시간과 만남 장소(선택). 장소를 주지 않으면 방의 출발지가 만남 장소다.
 *  좌표와 이름은 함께 와야 한다 - 이름만 바뀌면 도착 판정이, 좌표만 바뀌면 화면이 어긋난다
 */
public record MeetingPlan(Instant departureAt, Location departure, String departurePlace) {
    public MeetingPlan {
        if((departure == null) != (departurePlace == null)) {
			throw new BusinessException(MatchingErrorCode.INCOMPLETE_MEETING_PLACE);
		}
    }

    boolean hasPlace() {
        return departure != null;
    }
}
