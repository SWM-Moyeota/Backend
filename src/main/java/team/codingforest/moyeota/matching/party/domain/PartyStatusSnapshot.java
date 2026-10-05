package team.codingforest.moyeota.matching.party.domain;

import java.util.List;

/**
 *  "지금 방이 어떤 상태이고 누가 있는가"만 담는 읽기 모델. 놓친 SSE 신호를 잡는 안전망 폴링용이다.
 *  멤버는 ID 만 읽는다 - 프로필·완주 횟수는 방 상세의 몫이다.
 */
public record PartyStatusSnapshot(Long partyId, PartyStatus status, List<Long> memberIds) {
}
