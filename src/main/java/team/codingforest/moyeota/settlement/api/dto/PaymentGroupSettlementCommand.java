package team.codingforest.moyeota.settlement.api.dto;

import java.time.Instant;

public record PaymentGroupSettlementCommand(Long taxiDriverId, Long paymentGroupId, int totalFare, int platformPromotion,
                                            Instant paymentGroupCreatedAt) {
}
