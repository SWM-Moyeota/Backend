package team.codingforest.moyeota.payment.domain;

public record EasyPayDetail(String provider) implements MethodDetail {
    @Override
    public PaymentMethodType type() {
        return PaymentMethodType.EASY_PAY;
    }

    @Override
    public String label() {
        return provider;
    }

    @Override
    public String maskedNumber() {
        return null;
    }
}
