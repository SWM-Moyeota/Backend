package team.codingforest.moyeota.payment.infrastructure.jpa;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.domain.PaymentMethod;
import team.codingforest.moyeota.payment.domain.PaymentMethods;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentMethodJpa implements PaymentMethods {
    private final PaymentMethodJpaRepository delegate;

    @Override
    public PaymentMethod save(PaymentMethod method) {
        if(method.getId() != null) {
            PaymentMethodEntity entity = delegate.findById(method.getId())
                    .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_METHOD_NOT_FOUND));

            entity.update(method);
            delegate.save(entity);
            return entity.toDomain();
        }

        PaymentMethodEntity entity = PaymentMethodEntity.from(method);
        delegate.save(entity);
        return entity.toDomain();
    }

    @Override
    public Optional<PaymentMethod> findById(Long id) {
        return delegate.findById(id).map(PaymentMethodEntity::toDomain);
    }

    @Override
    public Optional<PaymentMethod> findActiveByUserId(Long userId) {
        return delegate.findActiveByUserId(userId).map(PaymentMethodEntity::toDomain);
    }
}
