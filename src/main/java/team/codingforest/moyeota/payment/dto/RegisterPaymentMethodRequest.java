package team.codingforest.moyeota.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RegisterPaymentMethodRequest(
        @Schema(description = "포트원 SDK 로 발급받은 빌링키. 발급 시 customer.id 에 유저 publicId 를 넣어야 한다", example = "billing-key-0196a3c2-...") @NotBlank String billingKey
) {
}
