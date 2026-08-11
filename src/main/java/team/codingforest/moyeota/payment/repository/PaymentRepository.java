package team.codingforest.moyeota.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.payment.domain.entity.Payment;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Query("select p from Payment p where p.paymentGroupId = :id and p.type == PaymentType.MAIN and p.cancelAt != null")
    List<Payment> findByPaymentGroupId(@Param("id") Long id);
}
