package team.codingforest.moyeota.payment.domain;

import java.util.Optional;

/** 결제사(포트원) 포트. 구현은 infrastructure 에 - 도메인·애플리케이션은 HTTP 를 모른다 */
public interface PaymentGateway {
    /** 앱이 발급받아 보낸 빌링키를 결제사에서 조회·검증 */
    BillingKeyInfo inspectBillingKey(String billingKey);

    /** 빌링키로 즉시 결제. 결제 자체의 실패는 결과로, 통신 장애는 GATEWAY_ERROR 예외로 */
    ChargeResult charge(ChargeOrder order);

    /** 결제사 기준 현재 상태 - 웹훅·복구 동기화용. 결제사에 없는 id 면 empty */
    Optional<GatewayPayment> findPayment(String pgPaymentId);

    void cancel(String pgPaymentId, String reason);

    void deleteBillingKey(String billingKey);
}
