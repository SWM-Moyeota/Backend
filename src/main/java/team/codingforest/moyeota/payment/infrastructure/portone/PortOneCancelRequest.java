package team.codingforest.moyeota.payment.infrastructure.portone;

/** POST /payments/{paymentId}/cancel - amount 를 생략하면 전액 취소 */
public record PortOneCancelRequest(String reason) {
}
