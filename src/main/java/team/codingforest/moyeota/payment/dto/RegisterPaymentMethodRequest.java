package team.codingforest.moyeota.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterPaymentMethodRequest(
        @Schema(description = "포트원 SDK(requestIssueBillingKey) 로 발급받은 빌링키. 발급 시 customer.customerId 에 유저 publicId 를 넣어야 한다",
                example = "billing-key-0196a3c2-7b1e-7c2a-9d3f-1a2b3c4d5e6f")
        @NotBlank @Size(max = 255) String billingKey
) {
    @Override
    public String toString() {
        return "RegisterPaymentMethodRequest[billingKey=REDACTED]";
    }
}
