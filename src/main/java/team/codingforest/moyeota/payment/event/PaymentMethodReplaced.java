package team.codingforest.moyeota.payment.event;

/** 삭제할 결제수단의 내부 ID만 outbox에 기록한다. 빌링키는 이벤트에 넣지 않는다. */
public record PaymentMethodReplaced(Long methodId) {
}