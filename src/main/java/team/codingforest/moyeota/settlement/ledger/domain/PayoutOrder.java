package team.codingforest.moyeota.settlement.ledger.domain;

public record PayoutOrder(Long payoutId, String payoutKey, int amount, PayoutAccount account) {
}
