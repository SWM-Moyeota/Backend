package team.codingforest.moyeota.payment.infrastructure.jpa;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.domain.PaymentGroup;
import team.codingforest.moyeota.payment.domain.PaymentGroups;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentGroupJpa implements PaymentGroups {
    private final PaymentGroupJpaRepository groups;
    private final PaymentJpaRepository payments;

    @Override
    public PaymentGroup save(PaymentGroup group) {
        PaymentGroupEntity entity;

        if(group.getId() == null) {
            entity = PaymentGroupEntity.from(group);
            groups.saveAndFlush(entity);        // 새 결제 id 가 바로 필요하다(청구 식별자 발급·대납 id 반환)
        }
        else {
            entity = groups.findById(group.getId())
                    .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_GROUP_NOT_FOUND));
            entity.update(group);               // 더티체킹
            groups.saveAndFlush(entity);
        }

        return entity.toDomain();
    }

    @Override
    public Optional<PaymentGroup> findById(Long id) {
        return groups.findById(id).map(PaymentGroupEntity::toDomain);
    }

    @Override
    public Optional<PaymentGroup> findByIdForUpdate(Long id) {
        return groups.findByIdForUpdate(id).map(PaymentGroupEntity::toDomain);
    }

    @Override
    public Optional<PaymentGroup> findByPartyId(Long partyId) {
        return groups.findByPartyId(partyId).map(PaymentGroupEntity::toDomain);
    }

    @Override
    public Optional<Long> findGroupIdByPaymentId(Long paymentId) {
        return payments.findGroupIdById(paymentId);
    }

    @Override
    public Optional<Long> findGroupIdByPgPaymentId(String pgPaymentId) {
        return payments.findGroupIdByPgPaymentId(pgPaymentId);
    }

    @Override
    public List<PaymentGroup> findAllByUserId(Long userId) {
        return groups.findAllByUserId(userId).stream().map(PaymentGroupEntity::toDomain).toList();
    }
}
