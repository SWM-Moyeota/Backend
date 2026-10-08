package team.codingforest.moyeota.payment.domain;

/** customerId 는 앱이 빌링키 발급 시 넣은 값 - 유저 publicId 와 같아야 본인 수단으로 인정 */
public record BillingKeyInfo(boolean issued, String customerId, MethodDetail detail) {
}
