package team.codingforest.moyeota.payment.domain;

/** pgPaymentId 가 null 이면 결제 통지가 아니라(빌링키 발급 등) 동기화할 것이 없는 이벤트 */
public record WebhookNotice(String type, String pgPaymentId) {
}
