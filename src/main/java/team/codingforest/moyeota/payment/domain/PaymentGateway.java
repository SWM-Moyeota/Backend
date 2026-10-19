package team.codingforest.moyeota.payment.domain;

/** 결제사(포트원) 포트. 구현은 infrastructure.portone 에 - 도메인·애플리케이션은 HTTP 를 모른다 */
public interface PaymentGateway {
    /** 앱이 발급받아 보낸 빌링키를 결제사에서 조회. 결제사에 없는 키면 INVALID_BILLING_KEY, 통신 장애면 GATEWAY_ERROR */
    BillingKeyInfo inspectBillingKey(String billingKey);

    /** 결제사에 보관된 빌링키 삭제. 이미 없는 키는 성공으로 본다 */
    void deleteBillingKey(String billingKey);
}
