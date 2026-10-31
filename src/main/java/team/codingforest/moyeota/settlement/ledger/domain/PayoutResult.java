package team.codingforest.moyeota.settlement.ledger.domain;

public sealed interface PayoutResult {

    record Succeeded(String pgId) implements PayoutResult {
    }

    record Failed(String reason) implements PayoutResult {
    }

    record Unknown(String reason) implements PayoutResult {
    }
}
