package team.codingforest.moyeota.settlement.api;

import team.codingforest.moyeota.settlement.api.dto.PaymentGroupSettlementCommand;

public interface SettlementAccess {
    void record(PaymentGroupSettlementCommand command);
}
