package team.codingforest.moyeota.payment.domain;

public record MobileDetail(String phoneNumber) implements MethodDetail {
    @Override
    public PaymentMethodType type() {
        return PaymentMethodType.MOBILE;
    }

    @Override
    public String label() {
        return "휴대폰";
    }

    @Override
    public String maskedNumber() {
        return phoneNumber;
    }
}
