package team.codingforest.moyeota.payment.infrastructure.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentMethodJpaRepository extends JpaRepository<PaymentMethodEntity, Long> {

    @Query("select m from PaymentMethodEntity m where m.userId = :userId and m.deletedAt is null")
    Optional<PaymentMethodEntity> findActiveByUserId(@Param("userId") Long userId);
}
