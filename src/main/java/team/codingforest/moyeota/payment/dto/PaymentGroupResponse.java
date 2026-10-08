package team.codingforest.moyeota.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.codingforest.moyeota.payment.domain.PaymentGroup;
import team.codingforest.moyeota.payment.domain.PaymentGroupStatus;

import java.util.List;

public record PaymentGroupResponse(
        Long groupId,
        Long partyId,
        @Schema(description = "미터기 요금(원)") int fare,
        int passengerCount,
        @Schema(description = "아직 수금되지 않은 요금(원). 0 이면 COMPLETED") int remainingBalance,
        @Schema(description = "1인당 플랫폼 이용료(원)") int platformCharge,
        PaymentGroupStatus status,
        List<PaymentResponse> payments
) {
    public static PaymentGroupResponse from(PaymentGroup group) {
        List<PaymentResponse> payments = group.getPayments().stream()
                .map(p -> PaymentResponse.from(group, p))
                .toList();

        return new PaymentGroupResponse(group.getId(), group.getPartyId(), group.getFare(), group.getPassengerCount(),
                group.getRemainingBalance(), group.getPlatformCharge(), group.getStatus(), payments);
    }
}
