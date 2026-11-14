package team.codingforest.moyeota.matching.party.domain;

/**
 *  사용자에게 보여줄 예약 방 검색 결과 한 건
 */
public record NearbyReservation(ReservationSummary reservation, Boolean destinationNearby) {
}
