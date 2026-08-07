package team.codingforest.moyeota.payment.dto;

public record PaymentGroupReq(Long matchId, int totalFare, Long partenrId, int passengerCount) {
}
