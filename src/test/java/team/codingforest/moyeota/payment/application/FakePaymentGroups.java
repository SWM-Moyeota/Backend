package team.codingforest.moyeota.payment.application;

import team.codingforest.moyeota.payment.domain.Payment;
import team.codingforest.moyeota.payment.domain.PaymentGroup;
import team.codingforest.moyeota.payment.domain.PaymentGroups;
import team.codingforest.moyeota.payment.domain.PaymentLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 저장 시 id 를 매기고, 조회마다 새 객체를 돌려주는 가짜 - JPA 처럼 "다시 읽은 객체" 의미를 흉내낸다 */
class FakePaymentGroups implements PaymentGroups {
    private final Map<Long, PaymentGroup> store = new LinkedHashMap<>();
    private long groupSeq = 0;
    private long paymentSeq = 0;
    private long logSeq = 0;

    @Override
    public PaymentGroup save(PaymentGroup group) {
        Long id = group.getId() != null ? group.getId() : ++groupSeq;
        PaymentGroup stored = withIds(id, group);
        store.put(id, stored);
        return copy(stored);
    }

    @Override
    public Optional<PaymentGroup> findById(Long id) {
        return Optional.ofNullable(store.get(id)).map(this::copy);
    }

    @Override
    public Optional<PaymentGroup> findByIdForUpdate(Long id) {
        return findById(id);
    }

    @Override
    public Optional<PaymentGroup> findByPartyId(Long partyId) {
        return store.values().stream().filter(g -> g.getPartyId().equals(partyId)).findFirst().map(this::copy);
    }

    @Override
    public Optional<Long> findGroupIdByPaymentId(Long paymentId) {
        for(PaymentGroup g : store.values()) {
            for(Payment p : g.getPayments()) if(paymentId.equals(p.getId())) return Optional.of(g.getId());
        }
        return Optional.empty();
    }

    @Override
    public Optional<Long> findGroupIdByPgPaymentId(String pgPaymentId) {
        for(PaymentGroup g : store.values()) {
            for(Payment p : g.getPayments()) if(pgPaymentId.equals(p.getPgPaymentId())) return Optional.of(g.getId());
        }
        return Optional.empty();
    }

    @Override
    public List<PaymentGroup> findAllByUserId(Long userId) {
        return store.values().stream()
                .filter(g -> g.getPayments().stream().anyMatch(p -> p.isOwnedBy(userId)))
                .sorted(Comparator.comparing(PaymentGroup::getId).reversed())
                .map(this::copy)
                .toList();
    }

    private PaymentGroup withIds(Long id, PaymentGroup group) {
        List<Payment> payments = new ArrayList<>();
        for(Payment p : group.getPayments()) {
            Long paymentId = p.getId() != null ? p.getId() : ++paymentSeq;
            List<PaymentLog> logs = new ArrayList<>();
            for(PaymentLog l : p.getLogs()) {
                logs.add(l.id() != null ? l : new PaymentLog(++logSeq, l.amount(), l.status(), l.type(), l.failCode(), l.createdAt()));
            }
            payments.add(Payment.restore(paymentId, p.getUserId(), p.getAmount(), p.getType(), p.getCurrency(), p.getOrderName(), p.getCreatedAt(),
                    p.getPgPaymentId(), p.getPaymentMethodId(), p.getStatus(), p.getFailCode(), p.getCanceledAt(), p.getCancelReason(), logs));
        }
        return PaymentGroup.restore(id, group.getPartyId(), group.getDriverId(), group.getFare(), group.getPassengerCount(), group.getPlatformCharge(),
                group.getCreatedAt(), group.getRemainingBalance(), group.getStatus(), payments);
    }

    private PaymentGroup copy(PaymentGroup group) {
        return withIds(group.getId(), group);
    }
}
