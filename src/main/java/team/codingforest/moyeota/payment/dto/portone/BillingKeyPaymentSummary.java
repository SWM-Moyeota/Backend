package team.codingforest.moyeota.payment.dto.portone;

/** 결제 완료 요약. paidAt은 RFC 3339 형식의 결제 완료 시각이다. */
public record BillingKeyPaymentSummary(String pgTxId, String paidAt) {
}
