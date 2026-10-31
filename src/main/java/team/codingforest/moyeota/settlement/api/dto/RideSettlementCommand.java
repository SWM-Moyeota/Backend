package team.codingforest.moyeota.settlement.api.dto;

import java.time.Instant;

public record RideSettlementCommand(Long partyId, Long taxiDriverId, int totalFare, int platformPromotion, Instant occurredAt) {
}
