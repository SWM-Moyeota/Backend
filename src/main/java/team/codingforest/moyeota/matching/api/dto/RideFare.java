package team.codingforest.moyeota.matching.api.dto;

/**
 *  @param partyId   방
 *  @param totalFare 실제 택시비
 *  @param myAmount  내 몫 - 받는 사람은 택시비에서 보내는 사람 몫을 뺀 금액, 보내는 사람은 자기가 보낼 금액
 */
public record RideFare(Long partyId, int totalFare, int myAmount) {
}
