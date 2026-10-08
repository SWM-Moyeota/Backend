package team.codingforest.moyeota.payment.infrastructure.portone;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneBillingKeyPaymentResponse(Payment payment) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payment(String pgTxId, Instant paidAt) {}
}
