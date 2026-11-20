package team.codingforest.moyeota.payment.dto.portone;

import team.codingforest.moyeota.payment.entity.enums.PaymentCurrency;

public record PortOnePaymentRequest(String billingKey, String orderName, PaymentAmount amount, PaymentCurrency currency) {
}
