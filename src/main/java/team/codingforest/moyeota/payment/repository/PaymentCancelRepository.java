package team.codingforest.moyeota.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.payment.entity.PaymentCancel;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {
}
