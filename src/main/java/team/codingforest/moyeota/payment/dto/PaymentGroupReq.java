package team.codingforest.moyeota.payment.dto;

import java.util.List;

public record PaymentGroupReq(List<Long> users, int matchId, int total_fare) {
}
