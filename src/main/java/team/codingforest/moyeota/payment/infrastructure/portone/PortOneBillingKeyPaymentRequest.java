package team.codingforest.moyeota.payment.infrastructure.portone;

import com.fasterxml.jackson.annotation.JsonInclude;

/** POST /payments/{paymentId}/billing-key */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PortOneBillingKeyPaymentRequest(String billingKey, String orderName, Amount amount, String currency, Customer customer) {

    public record Amount(long total) {}

    public record Customer(String id) {}
}
