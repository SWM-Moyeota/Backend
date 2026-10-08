package team.codingforest.moyeota.matching.api.dto;

import java.util.List;

/** 운행 종료(IN_RIDE → FINISHED) 시 발행. 결제 모듈이 받아 방 구성원에게 요금을 나눠 청구한다 */
public record RideCompletedEvent(Long partyId, Long driverId, int fare, String departure, String destination, List<Long> memberIds) {
}
