package team.codingforest.moyeota.payment.domain;

public enum PaymentStatus {
    /** 청구 대기 또는 결제사 응답 대기 */
    READY,
    PAID,
    FAILED,
    /** 결제 후 취소(환불) */
    CANCELED,
    /** 실패한 분담금을 다른 멤버가 대납해 종결 */
    COVERED;

    public boolean isFinal() {
        return this == PAID || this == CANCELED || this == COVERED;
    }
}
