package team.codingforest.moyeota.settlement.ledger.domain;

import java.util.Optional;

public interface PayoutGateway {
    PayoutResult request(PayoutOrder order);
    Optional<PayoutResult> find(String payoutKey);
}
