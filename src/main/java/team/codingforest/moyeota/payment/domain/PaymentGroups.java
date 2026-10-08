package team.codingforest.moyeota.payment.domain;

import java.util.List;
import java.util.Optional;

public interface PaymentGroups {
    PaymentGroup save(PaymentGroup group);
    Optional<PaymentGroup> findById(Long id);
    Optional<PaymentGroup> findByIdForUpdate(Long id);
    Optional<PaymentGroup> findByPartyId(Long partyId);
    Optional<Long> findGroupIdByPaymentId(Long paymentId);
    Optional<Long> findGroupIdByPgPaymentId(String pgPaymentId);
    /** 유저가 청구받은(분담금·대납) 그룹들 - 최신순 */
    List<PaymentGroup> findAllByUserId(Long userId);
}
