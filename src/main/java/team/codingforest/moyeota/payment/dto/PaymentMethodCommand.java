package team.codingforest.moyeota.payment.dto;

import team.codingforest.moyeota.payment.domain.entity.PaymentMethodType;

public record PaymentMethodCommand(String billingKey, Long userId, PaymentMethodType type) {

}
