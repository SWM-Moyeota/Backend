package team.codingforest.moyeota.matching.party.domain;

/**
 *  지도 목록용 읽기 모델. 멤버 목록·경로(route) 없이 마커에 필요한 값만 담는다.
 *  currentMembers 는 JPQL count 결과를 그대로 받으므로 Long 이어야 한다(Integer 면 생성자 매칭 실패로 기동이 깨진다).
 */
public record PartySummary(Long id, String departure, String destination, Long currentMembers,
                           Integer capacity, PartyStatus status, Double departureLat, Double departureLng) {
}
