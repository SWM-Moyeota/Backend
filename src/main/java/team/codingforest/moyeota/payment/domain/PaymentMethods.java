package team.codingforest.moyeota.payment.domain;

import java.util.Optional;

public interface PaymentMethods {
    PaymentMethod save(PaymentMethod method);
    Optional<PaymentMethod> findById(Long id);
    Optional<PaymentMethod> findActiveByUserId(Long userId);
}
