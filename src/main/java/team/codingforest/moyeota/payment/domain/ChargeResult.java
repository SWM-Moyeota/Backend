package team.codingforest.moyeota.payment.domain;

import java.time.Instant;

public record ChargeResult(boolean paid, Instant paidAt, String failCode) {

    public static ChargeResult paid(Instant paidAt) {
        return new ChargeResult(true, paidAt, null);
    }

    public static ChargeResult failed(String failCode) {
        return new ChargeResult(false, null, failCode);
    }
}
