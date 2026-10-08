package team.codingforest.moyeota.payment.domain;

import java.time.Instant;

public record GatewayPayment(GatewayPaymentStatus status, Instant paidAt, String failCode, String cancelReason, Instant canceledAt) {

    public enum GatewayPaymentStatus {
        /** 아직 결제사가 결론을 내지 않음 */
        PENDING, PAID, FAILED, CANCELED
    }
}
