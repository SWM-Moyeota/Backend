package team.codingforest.moyeota.matching.party.domain;

import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;

/**
 *  지도 목록용 읽기 도메인
 */
public record PartySummary(Long id, String departure, String destination, Long currentMembers,
                           Integer capacity, PartyStatus status, Double departureLat, Double departureLng) {
}
