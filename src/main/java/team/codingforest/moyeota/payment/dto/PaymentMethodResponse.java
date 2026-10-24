package team.codingforest.moyeota.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.codingforest.moyeota.payment.entity.Card;
import team.codingforest.moyeota.payment.entity.PaymentMethod;
import team.codingforest.moyeota.payment.entity.PaymentMethodType;

import java.time.Instant;

public record PaymentMethodResponse(
        Long id,
        PaymentMethodType type,
        @Schema(description = "카드명. 없으면 브랜드", example = "신한카드") String label,
        @Schema(description = "결제사가 마스킹한 카드번호", example = "1234-56**-****-7890") String maskedNumber,
        @Schema(description = "카드 브랜드", example = "VISA") String brand,
        Instant createdAt,
        boolean isDefault
) {
    public static PaymentMethodResponse from(PaymentMethod method) {
        Card card = method.getCard();
        return new PaymentMethodResponse(method.getId(), method.getType(), card.getLabel(),
                card.getNumber(), card.getBrand(), method.getCreatedAt(), method.isDefaultMethod());
    }
}
