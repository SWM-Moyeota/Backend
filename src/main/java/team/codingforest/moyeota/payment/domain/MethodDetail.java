package team.codingforest.moyeota.payment.domain;

/** 결제수단 종류별 표시 정보. 빌링키 자체는 PaymentMethod 가 들고, 여기엔 사용자에게 보여줄 값만 둔다 */
public sealed interface MethodDetail permits CardDetail, EasyPayDetail, MobileDetail {
    PaymentMethodType type();
    String label();
    String maskedNumber();
}
