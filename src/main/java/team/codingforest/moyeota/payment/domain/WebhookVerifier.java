package team.codingforest.moyeota.payment.domain;

/** 결제사 웹훅의 서명을 검증하고 어떤 결제에 대한 통지인지 꺼낸다 */
public interface WebhookVerifier {
    WebhookNotice verify(String webhookId, String timestamp, String signature, String body);
}
