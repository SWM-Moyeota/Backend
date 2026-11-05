package team.codingforest.moyeota.matching.party.domain;

/**
 *  예약 방 검색 결과 한 건. destinationNearby 는 도착지를 넣고 검색했을 때만 채워진다(도착지도 반경 안이면 true).
 *  도착지 없이 검색하면 null 이다
 */
public record NearbyReservation(ReservationSummary reservation, Boolean destinationNearby) {
}
