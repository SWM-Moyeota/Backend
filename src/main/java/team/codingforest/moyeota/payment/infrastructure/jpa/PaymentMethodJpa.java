package team.codingforest.moyeota.payment.infrastructure.jpa;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DataIntegrityViolationException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.domain.PaymentMethod;
import team.codingforest.moyeota.payment.domain.PaymentMethods;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import java.util.Optional;
import java.sql.SQLException;

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
            // 기존 기본 카드 해제를 먼저 반영해야 새 기본 카드 지정이 UNIQUE 제약과 충돌하지 않는다.
            delegate.saveAndFlush(entity);
            return entity.toDomain();
        }

        PaymentMethodEntity entity = PaymentMethodEntity.from(method);
        try {
            delegate.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            // 동시 등록과 이미 사용한 빌링키는 500 대신 409. 서비스 트랜잭션 전체를 롤백한다.
            for(Throwable cause = e; cause != null; cause = cause.getCause()) {
                if(cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new BusinessException(PaymentErrorCode.PAYMENT_METHOD_CONFLICT);
                }
            }
            throw e;
        }
        return entity.toDomain();
    }

    @Override
    public Optional<PaymentMethod> findById(Long id) {
        return delegate.findById(id).map(PaymentMethodEntity::toDomain);
    }

    @Override
    public Optional<PaymentMethod> findByBillingKey(String billingKey) {
        return delegate.findByBillingKey(billingKey).map(PaymentMethodEntity::toDomain);
    }

    @Override
    public Optional<PaymentMethod> findDefaultByUserId(Long userId) {
        return delegate.findDefaultByUserId(userId).map(PaymentMethodEntity::toDomain);
    }
    @Override
    public List<PaymentMethod> findAllActiveByUserId(Long userId) {
        return delegate.findAllActiveByUserId(userId).stream().map(PaymentMethodEntity::toDomain).toList();
    }

    @Override
    public List<PaymentMethod> findAllActiveByUserIdForUpdate(Long userId) {
        return delegate.findAllActiveByUserIdForUpdate(userId).stream().map(PaymentMethodEntity::toDomain).toList();
    }
}
