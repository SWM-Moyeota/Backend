package team.codingforest.moyeota.payment.application;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.domain.BillingKeyInfo;
import team.codingforest.moyeota.payment.domain.PaymentGateway;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** 결제사 가짜. billingKeys 에 심어 둔 키만 조회된다. down 이면 모든 호출이 통신 장애, deleteDown 이면 삭제만 실패 */
class FakePaymentGateway implements PaymentGateway {
    final Map<String, BillingKeyInfo> billingKeys = new HashMap<>();
    final Set<String> deletedBillingKeys = new LinkedHashSet<>();
    boolean down;
    boolean deleteDown;

    @Override
    public BillingKeyInfo inspectBillingKey(String billingKey) {
        if(down) throw new BusinessException(PaymentErrorCode.GATEWAY_ERROR);
        BillingKeyInfo info = billingKeys.get(billingKey);
        if(info == null) throw new BusinessException(PaymentErrorCode.INVALID_BILLING_KEY);
        return info;
    }

    @Override
    public void deleteBillingKey(String billingKey) {
        if(down || deleteDown) throw new BusinessException(PaymentErrorCode.GATEWAY_ERROR);
        deletedBillingKeys.add(billingKey);
    }
}
