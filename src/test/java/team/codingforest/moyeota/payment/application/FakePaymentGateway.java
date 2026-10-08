package team.codingforest.moyeota.payment.application;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.domain.BillingKeyInfo;
import team.codingforest.moyeota.payment.domain.ChargeOrder;
import team.codingforest.moyeota.payment.domain.ChargeResult;
import team.codingforest.moyeota.payment.domain.GatewayPayment;
import team.codingforest.moyeota.payment.domain.PaymentGateway;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 *  결제사 가짜. 기본은 전부 승인. 빌링키별로 거절 코드를 심거나(rejections), 통신 장애(down)를 켤 수 있다.
 *  승인된 결제는 remote 에 기록돼 findPayment 가 PAID 를 돌려준다 - 응답 유실 후 복구 시나리오용.
 */
class FakePaymentGateway implements PaymentGateway {
    final List<ChargeOrder> charged = new ArrayList<>();
    final Map<String, String> rejections = new HashMap<>();          // billingKey → failCode
    final Map<String, GatewayPayment> remote = new HashMap<>();      // pgPaymentId → 결제사 기준 상태
    final Map<String, BillingKeyInfo> billingKeys = new HashMap<>();
    final Set<String> deletedBillingKeys = new HashSet<>();
    boolean down;

    @Override
    public BillingKeyInfo inspectBillingKey(String billingKey) {
        if(down) throw new BusinessException(PaymentErrorCode.GATEWAY_ERROR);
        BillingKeyInfo info = billingKeys.get(billingKey);
        if(info == null) throw new BusinessException(PaymentErrorCode.INVALID_BILLING_KEY);
        return info;
    }

    @Override
    public ChargeResult charge(ChargeOrder order) {
        if(down) throw new BusinessException(PaymentErrorCode.GATEWAY_ERROR);
        charged.add(order);

        String failCode = rejections.get(order.billingKey());
        if(failCode != null) {
            remote.put(order.pgPaymentId(), new GatewayPayment(GatewayPayment.GatewayPaymentStatus.FAILED, null, failCode, null, null));
            return ChargeResult.failed(failCode);
        }

        Instant paidAt = Instant.now();
        remote.put(order.pgPaymentId(), new GatewayPayment(GatewayPayment.GatewayPaymentStatus.PAID, paidAt, null, null, null));
        return ChargeResult.paid(paidAt);
    }

    @Override
    public Optional<GatewayPayment> findPayment(String pgPaymentId) {
        if(down) throw new BusinessException(PaymentErrorCode.GATEWAY_ERROR);
        return Optional.ofNullable(remote.get(pgPaymentId));
    }

    @Override
    public void cancel(String pgPaymentId, String reason) {
        remote.put(pgPaymentId, new GatewayPayment(GatewayPayment.GatewayPaymentStatus.CANCELED, Instant.now(), null, reason, Instant.now()));
    }

    @Override
    public void deleteBillingKey(String billingKey) {
        if(down) throw new BusinessException(PaymentErrorCode.GATEWAY_ERROR);
        deletedBillingKeys.add(billingKey);
    }
}
