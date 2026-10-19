package team.codingforest.moyeota.payment.infrastructure.jpa;

import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentMethodJpaRepository extends JpaRepository<PaymentMethodEntity, Long> {
    Optional<PaymentMethodEntity> findByBillingKey(String billingKey);

    @Query("select m from PaymentMethodEntity m where m.userId = :userId and m.deletedAt is null and m.defaultMethod = true")
    Optional<PaymentMethodEntity> findDefaultByUserId(@Param("userId") Long userId);

    @Query("select m from PaymentMethodEntity m where m.userId = :userId and m.deletedAt is null order by m.id")
    List<PaymentMethodEntity> findAllActiveByUserId(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from PaymentMethodEntity m where m.userId = :userId and m.deletedAt is null order by m.id")
    List<PaymentMethodEntity> findAllActiveByUserIdForUpdate(@Param("userId") Long userId);
}
