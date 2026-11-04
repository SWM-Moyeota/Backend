package team.codingforest.moyeota.settlement.ledger.domain;

/**
 * 결제사에 보낼 지급 요청. 다시 보낼 때도 같은 값이 나가도록 지급 행에 남긴 값만 씀
 *
 * @param payoutId  지급 id
 * @param payoutKey 결제사 참조 id 이자 멱등키
 * @param amount    지급 금액
 * @param sellerId  받을 결제사 셀러 id
 */
public record PayoutOrder(Long payoutId, String payoutKey, int amount, String sellerId) {
}
