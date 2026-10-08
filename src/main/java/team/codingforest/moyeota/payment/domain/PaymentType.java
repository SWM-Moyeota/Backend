package team.codingforest.moyeota.payment.domain;

public enum PaymentType {
    /** 운행 요금을 인원수로 나눈 본인 분담금 */
    SHARE,
    /** 실패한 다른 멤버 분담금까지 한 사람이 대신 결제 */
    COVER
}
