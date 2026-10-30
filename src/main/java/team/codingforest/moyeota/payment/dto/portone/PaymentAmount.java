package team.codingforest.moyeota.payment.dto.portone;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

public record PaymentAmount(Long total) {

    public PaymentAmount {
        if (total == null || total <= 0) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_AMOUNT);
        }
    }

}
