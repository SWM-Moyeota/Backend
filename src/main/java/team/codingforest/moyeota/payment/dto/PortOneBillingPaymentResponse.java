package team.codingforest.moyeota.payment.dto;

public record PortOneBillingPaymentResponse(
        String pgTxId,
        String paidAt
) {
}
