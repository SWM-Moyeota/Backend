package team.codingforest.moyeota.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.payment.domain.entity.PaymentGroup;

public interface PaymentGroupRepository extends JpaRepository<PaymentGroup, Long> {
}
