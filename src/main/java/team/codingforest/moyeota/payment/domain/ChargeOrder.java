package team.codingforest.moyeota.payment.domain;

/** 결제사에 보낼 청구 1건. customerId(유저 publicId)는 결제사 쪽 고객 식별용 - 없으면 생략 가능 */
public record ChargeOrder(String pgPaymentId, String billingKey, int amount, Currency currency, String orderName, Long userId, String customerId) {

    public ChargeOrder withCustomerId(String customerId) {
        return new ChargeOrder(pgPaymentId, billingKey, amount, currency, orderName, userId, customerId);
    }
}
