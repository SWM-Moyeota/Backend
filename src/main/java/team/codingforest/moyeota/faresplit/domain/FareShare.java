package team.codingforest.moyeota.faresplit.domain;

/**
 *  탑승 기록에 채울 정산 금액(읽기 모델) - 내가 받는 사람이거나 보내는 사람인 정산 하나.
 */
public record FareShare(Long partyId, Long payerId, int totalFare, int amountPerPerson, int senderCount) {

    public int amountOf(Long memberId) {
        return payerId.equals(memberId) ? FareSplit.payerShare(totalFare, amountPerPerson, senderCount) : amountPerPerson;
    }
}
