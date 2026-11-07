package team.codingforest.moyeota.faresplit.domain;

/**
 *  탑승 기록에 채울 정산 금액(읽기 모델) - 내가 받는 사람이거나 보내는 사람인 정산 하나. JPQL 선택 목록을 그대로 받으므로 필드 순서·타입을 쿼리와 맞춘다
 *
 *  @param partyId         방
 *  @param payerId         받는 사람
 *  @param totalFare       실제 택시비
 *  @param amountPerPerson 1인당 보낼 금액
 *  @param senderCount     보내는 사람 수(받는 사람 제외)
 */
public record FareShare(Long partyId, Long payerId, int totalFare, int amountPerPerson, int senderCount) {

    /** 내 몫 - 받는 사람은 payerShare, 보내는 사람은 1인당 금액 */
    public int amountOf(Long memberId) {
        return payerId.equals(memberId) ? FareSplit.payerShare(totalFare, amountPerPerson, senderCount) : amountPerPerson;
    }
}
