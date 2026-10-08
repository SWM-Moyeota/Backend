package team.codingforest.moyeota.payment.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {

    @Query("select p.group.id from PaymentEntity p where p.id = :id")
    Optional<Long> findGroupIdById(@Param("id") Long id);

    @Query("select p.group.id from PaymentEntity p where p.pgPaymentId = :pgPaymentId")
    Optional<Long> findGroupIdByPgPaymentId(@Param("pgPaymentId") String pgPaymentId);
}
