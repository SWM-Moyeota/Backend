package team.codingforest.moyeota.payment.domain;

/** number 는 결제사가 마스킹해서 준 값(예: 1234-56**-****-7890)만 저장한다 */
public record CardDetail(String name, String number, String brand) implements MethodDetail {
    @Override
    public PaymentMethodType type() {
        return PaymentMethodType.CARD;
    }

    @Override
    public String label() {
        return name != null ? name : brand;
    }

    @Override
    public String maskedNumber() {
        return number;
    }
}
