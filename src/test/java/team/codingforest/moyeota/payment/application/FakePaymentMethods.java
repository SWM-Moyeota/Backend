package team.codingforest.moyeota.payment.application;

import team.codingforest.moyeota.payment.domain.PaymentMethod;
import team.codingforest.moyeota.payment.domain.PaymentMethods;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

class FakePaymentMethods implements PaymentMethods {
    private final Map<Long, PaymentMethod> store = new LinkedHashMap<>();
    private long seq = 0;

    @Override
    public PaymentMethod save(PaymentMethod method) {
        Long id = method.getId() != null ? method.getId() : ++seq;
        PaymentMethod stored = PaymentMethod.restore(id, method.getUserId(), method.getBillingKey(), method.getDetail(), method.getCreatedAt(), method.getDeletedAt());
        store.put(id, stored);
        return stored;
    }

    @Override
    public Optional<PaymentMethod> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<PaymentMethod> findActiveByUserId(Long userId) {
        return store.values().stream().filter(m -> m.getUserId().equals(userId) && m.isActive()).findFirst();
    }
}
