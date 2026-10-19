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
        PaymentMethod stored = PaymentMethod.restore(id, method.getUserId(), method.getBillingKey(), method.getCard(), method.getCreatedAt(), method.getDeletedAt(), method.isDefaultMethod());
        store.put(id, stored);
        return stored;
    }

    @Override
    public Optional<PaymentMethod> findByBillingKey(String billingKey) {
        return store.values().stream().filter(m -> m.hasBillingKey(billingKey)).findFirst();
    }

    @Override
    public Optional<PaymentMethod> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<PaymentMethod> findDefaultByUserId(Long userId) {
        return store.values().stream().filter(m -> m.getUserId().equals(userId) && m.isActive() && m.isDefaultMethod()).findFirst();
    }

    @Override
    public java.util.List<PaymentMethod> findAllActiveByUserId(Long userId) {
        return store.values().stream().filter(m -> m.getUserId().equals(userId) && m.isActive()).toList();
    }

    @Override
    public java.util.List<PaymentMethod> findAllActiveByUserIdForUpdate(Long userId) {
        return findAllActiveByUserId(userId);
    }

    long count() {
        return store.size();
    }
}
