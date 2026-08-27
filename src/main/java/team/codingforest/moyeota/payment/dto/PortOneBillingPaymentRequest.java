package team.codingforest.moyeota.payment.dto;

import team.codingforest.moyeota.payment.domain.entity.Currency;

public record PortOneBillingPaymentRequest(
        String billingKey,
        String orderName,
        int amount,
        Currency currency
) {
}
