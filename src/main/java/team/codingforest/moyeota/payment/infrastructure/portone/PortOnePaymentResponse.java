package team.codingforest.moyeota.payment.infrastructure.portone;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import team.codingforest.moyeota.payment.domain.GatewayPayment;

import java.time.Instant;
import java.util.List;

/**
 *  GET /payments/{paymentId}
 *  status: READY | PENDING | VIRTUAL_ACCOUNT_ISSUED | PAY_PENDING | PAID | FAILED | PARTIAL_CANCELLED | CANCELLED
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOnePaymentResponse(String status, Instant paidAt, Failure failure, List<Cancellation> cancellations) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Failure(String reason, String pgCode, String pgMessage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Cancellation(String status, String reason, Instant cancelledAt) {}

    public GatewayPayment toDomain() {
        return switch(status == null ? "" : status) {
            case "PAID", "PARTIAL_CANCELLED" -> new GatewayPayment(GatewayPayment.GatewayPaymentStatus.PAID, paidAt, null, null, null);
            case "FAILED" -> new GatewayPayment(GatewayPayment.GatewayPaymentStatus.FAILED, null, failCode(), null, null);
            case "CANCELLED" -> {
                Cancellation last = cancellations == null || cancellations.isEmpty() ? null : cancellations.get(cancellations.size() - 1);
                yield new GatewayPayment(GatewayPayment.GatewayPaymentStatus.CANCELED, paidAt, null,
                        last == null ? null : last.reason(), last == null ? null : last.cancelledAt());
            }
            default -> new GatewayPayment(GatewayPayment.GatewayPaymentStatus.PENDING, null, null, null, null);
        };
    }

    private String failCode() {
        if(failure == null) return "FAILED";
        if(failure.pgCode() != null && !failure.pgCode().isBlank()) return failure.pgCode();
        return failure.reason() != null ? failure.reason() : "FAILED";
    }
}
