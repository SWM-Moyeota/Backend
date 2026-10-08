package team.codingforest.moyeota.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.codingforest.moyeota.payment.domain.Payment;
import team.codingforest.moyeota.payment.domain.PaymentGroup;
import team.codingforest.moyeota.payment.domain.PaymentStatus;
import team.codingforest.moyeota.payment.domain.PaymentType;

import java.time.Instant;

public record PaymentResponse(
        Long paymentId,
        Long groupId,
        Long partyId,
        Long userId,
        @Schema(description = "청구 금액(원). SHARE 는 분담금+플랫폼 이용료, COVER 는 미수금 전액") int amount,
        PaymentType type,
        PaymentStatus status,
        @Schema(description = "실패 사유 코드. NO_PAYMENT_METHOD 면 결제수단 등록 후 재시도", example = "NO_PAYMENT_METHOD") String failCode,
        String orderName,
        Instant createdAt
) {
    public static PaymentResponse from(PaymentGroup group, Payment payment) {
        return new PaymentResponse(payment.getId(), group.getId(), group.getPartyId(), payment.getUserId(), payment.getAmount(), payment.getType(),
                payment.getStatus(), payment.getFailCode(), payment.getOrderName(), payment.getCreatedAt());
    }
}
