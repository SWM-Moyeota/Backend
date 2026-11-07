package team.codingforest.moyeota.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.payment.entity.PaymentLog;

public interface PaymentLogRepository extends JpaRepository<PaymentLog, Long> {
}
