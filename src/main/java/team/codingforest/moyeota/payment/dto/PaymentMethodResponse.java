package team.codingforest.moyeota.payment.dto;

public record PaymentMethodResponse(String name, String number, String brand) {

    public static PaymentMethodResponse from(String name, String number, String brand) {
        return new PaymentMethodResponse(name, number, brand);
    }
}
