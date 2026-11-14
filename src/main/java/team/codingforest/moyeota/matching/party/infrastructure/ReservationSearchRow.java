package team.codingforest.moyeota.matching.party.infrastructure;

/**
 *  예약 방 검색 1단계(native) 결과. 고른 방 id 와 도착지 근처 여부만 담고, 요약은 2단계에서 id 로 읽는다
 */
public record ReservationSearchRow(Long id, Boolean destinationNearby) {
}
