package team.codingforest.moyeota.payment.dto;

import java.util.List;

public record PaymentGroupReq(Long matchId, int totalFare, Long partnerId, int passengerCount, List<Long> userIds) {
}
