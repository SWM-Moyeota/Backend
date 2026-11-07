package team.codingforest.moyeota.matching.party.infrastructure;

/**
 *  방 id 와 출발지·도착지 이름. JPQL 선택 목록을 그대로 받으므로 필드 순서·타입을 쿼리와 맞춘다
 */
public record PartyPlaceRow(Long id, String departure, String destination) {
}
