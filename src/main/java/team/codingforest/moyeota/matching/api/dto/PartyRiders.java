package team.codingforest.moyeota.matching.api.dto;

import java.util.List;

/**
 *  방의 멤버와 실제로 탄 사람 - 택시비 정산이 누가 나눠 낼지 정할 때 쓴다
 *
 *  @param partyId   방
 *  @param finished  동승이 끝났는지(FINISHED)
 *  @param memberIds 방 멤버, 합류 순
 *  @param riderIds  탑승자 - 출발지에 도착했고 노쇼가 아닌 멤버, 합류 순
 */
public record PartyRiders(Long partyId, boolean finished, List<Long> memberIds, List<Long> riderIds) {
}
