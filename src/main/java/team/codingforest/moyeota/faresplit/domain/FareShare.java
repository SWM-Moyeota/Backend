package team.codingforest.moyeota.faresplit.domain;

/**
 * 탑승 기록에 채울 정산 금액
 */
public record FareShare(Long partyId, int totalFare, int myPayment) {
}
