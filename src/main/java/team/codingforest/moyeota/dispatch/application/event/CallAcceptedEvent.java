package team.codingforest.moyeota.dispatch.application.event;

import java.util.List;

/** 기사 배정이 커밋된 뒤 콜을 마감해야 할 때. losers 는 수락자를 뺀 나머지 후보 */
public record CallAcceptedEvent(Long partyId, Long driverId, List<Long> losers) {
}
