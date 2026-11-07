package team.codingforest.moyeota.matching.faresplit.domain;

import java.util.List;

/**
 *  정산을 시작할 때 보는 방 명단 - 누가 멤버이고 누가 실제로 탔는지. 탑승자 규칙은 matching 이 정하고, 서비스가 창구(PartyAccess)에서 읽어 옮겨 담는다
 *
 *  @param partyId   방
 *  @param finished  동승이 끝났는지(FINISHED)
 *  @param memberIds 방 멤버, 합류 순
 *  @param riderIds  탑승자, 합류 순
 */
public record RideRoster(Long partyId, boolean finished, List<Long> memberIds, List<Long> riderIds) {
}
