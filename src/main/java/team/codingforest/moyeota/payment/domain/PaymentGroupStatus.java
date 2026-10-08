package team.codingforest.moyeota.payment.domain;

public enum PaymentGroupStatus {
    /** 아직 응답을 못 받은 청구가 남아 있음 */
    IN_PROGRESS,
    /** 모든 청구가 끝났지만 실패분이 남아 있음 - 재시도 또는 대납 필요 */
    PARTIALLY_FAILED,
    /** 운행 요금 전액 수금 완료 */
    COMPLETED
}
