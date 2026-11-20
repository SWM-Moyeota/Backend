package team.codingforest.moyeota.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.payment.entity.PaymentSettlement;

public interface PaymentSettlementRepository extends JpaRepository<PaymentSettlement, Long> {
}
