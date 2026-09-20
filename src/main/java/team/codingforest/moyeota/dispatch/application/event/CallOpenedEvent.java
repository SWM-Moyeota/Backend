package team.codingforest.moyeota.dispatch.application.event;

import team.codingforest.moyeota.matching.api.PartySummary;

import java.util.List;

/** 탐색 트랜잭션이 커밋된 뒤 기사들에게 콜을 열어야 할 때. driverIds 는 이번 탐색에서 새로 찾은 기사만 */
public record CallOpenedEvent(Long partyId, List<Long> driverIds, PartySummary party) {
}
