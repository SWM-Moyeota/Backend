package team.codingforest.moyeota.payment.domain;

import java.util.UUID;

/** 결제사에 넘기는 결제 식별자. 시도마다 새로 만들어 "이미 결제된 id" 충돌을 피한다 */
public final class PgPaymentId {
    private static final String PREFIX = "moyeota-";

    private PgPaymentId() {}

    public static String next() {
        return PREFIX + UUID.randomUUID();
    }
}
