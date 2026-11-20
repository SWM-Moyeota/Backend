package team.codingforest.moyeota.payment.entity.enums;

/** 결제 처리 시작과 이후 결과를 이력에 기록하기 위한 상태. */
public enum PaymentLogStatus {
    /** 서버의 결제 처리 시작을 뜻하며, 외부 결제 요청의 전달을 보장하지 않는다. */
    PENDING,
    PAID,
    FAILED,
    CANCELED
}
