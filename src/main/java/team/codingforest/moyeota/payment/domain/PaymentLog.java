package team.codingforest.moyeota.payment.domain;

import java.time.Instant;

/** 결제 상태 전이 기록(append-only). id 가 null 이면 아직 저장되지 않은 새 기록 */
public record PaymentLog(Long id, int amount, PaymentStatus status, PaymentType type, String failCode, Instant createdAt) {

    public static PaymentLog of(Payment payment, Instant at) {
        return new PaymentLog(null, payment.getAmount(), payment.getStatus(), payment.getType(), payment.getFailCode(), at);
    }
}
