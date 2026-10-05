package team.codingforest.moyeota.matching.party.infrastructure;

import team.codingforest.moyeota.matching.party.domain.PartyStatus;

/** 방 상태 조회의 한 행. 멤버가 없는 방은 memberId 가 null 인 행 하나로 나온다(left join) */
public interface PartyStatusRow {
    PartyStatus getStatus();
    Long getMemberId();
}
