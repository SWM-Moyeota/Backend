package team.codingforest.moyeota.payment.dto;

import team.codingforest.moyeota.payment.entity.enums.PaymentStatus;

/** 사전결제 서비스가 승인 확인 후 반환하는 결제 결과. */
public record PaymentResultResponse(
        Long paymentId,
        String pgTxId,
        Long userId,
        int amount,
        PaymentStatus status
) {
}
