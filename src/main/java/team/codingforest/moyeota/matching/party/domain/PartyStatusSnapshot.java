package team.codingforest.moyeota.matching.party.domain;

/**
 *  "지금 방이 어떤 상태인가"만 담는 읽기 모델. 놓친 SSE 신호를 잡는 안전망 폴링용이다.
 *  currentMembers 는 JPQL count 결과를 그대로 받으므로 Long 이어야 한다.
 */
public record PartyStatusSnapshot(PartyStatus status, Long currentMembers) {
}
