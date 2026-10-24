package team.codingforest.moyeota.payment.domain;

import java.util.List;

import java.util.Optional;

/** 결제수단 저장소 포트. 구현은 infrastructure.jpa 에 */
public interface PaymentMethods {
    PaymentMethod save(PaymentMethod method);
    Optional<PaymentMethod> findById(Long id);
    Optional<PaymentMethod> findByBillingKey(String billingKey);
    Optional<PaymentMethod> findDefaultByUserId(Long userId);
    List<PaymentMethod> findAllActiveByUserId(Long userId);
    /** 등록·기본 카드 변경 시 기존 카드들을 잠가 변경을 직렬화한다. */
    List<PaymentMethod> findAllActiveByUserIdForUpdate(Long userId);
}
