package team.codingforest.moyeota.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.payment.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
