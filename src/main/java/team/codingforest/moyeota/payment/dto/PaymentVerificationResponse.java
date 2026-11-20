package team.codingforest.moyeota.payment.dto;

/** 결제 승인과 전액 취소까지 확인된 검증 결과. */
public record PaymentVerificationResponse(Long paymentId, boolean verified) {
}
