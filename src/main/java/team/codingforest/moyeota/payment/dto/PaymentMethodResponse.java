package team.codingforest.moyeota.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.codingforest.moyeota.payment.domain.PaymentMethod;
import team.codingforest.moyeota.payment.domain.PaymentMethodType;

import java.time.Instant;

public record PaymentMethodResponse(
        Long id,
        PaymentMethodType type,
        @Schema(description = "카드명/간편결제사/휴대폰", example = "신한카드") String label,
        @Schema(description = "마스킹된 카드번호 또는 휴대폰번호. 간편결제는 null", example = "1234-56**-****-7890") String maskedNumber,
        Instant createdAt
) {
    public static PaymentMethodResponse from(PaymentMethod method) {
        return new PaymentMethodResponse(method.getId(), method.getType(), method.getDetail().label(), method.getDetail().maskedNumber(), method.getCreatedAt());
    }
}
