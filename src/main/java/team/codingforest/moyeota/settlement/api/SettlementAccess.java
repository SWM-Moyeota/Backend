package team.codingforest.moyeota.settlement.api;

import team.codingforest.moyeota.settlement.api.dto.RideSettlementCommand;

public interface SettlementAccess {
    void record(RideSettlementCommand command);
}
