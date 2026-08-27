package team.codingforest.moyeota.payment.dto;

public record PaymentVerifyRequest(
        Long userId,
        Long paymentMethodId,
        Double departureLat,
        Double departureLng,
        Double destinationLat,
        Double destinationLng
) {
}
