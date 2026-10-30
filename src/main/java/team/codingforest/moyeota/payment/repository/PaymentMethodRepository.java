package team.codingforest.moyeota.payment.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.payment.entity.PaymentMethod;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    Optional<PaymentMethod> findByBillingKey(String billingKey);

    @Query("select m from PaymentMethod m where m.userId = :userId and m.deletedAt is null and m.defaultMethod = true")
    Optional<PaymentMethod> findDefaultByUserId(@Param("userId") Long userId);

    @Query("select m from PaymentMethod m where m.userId = :userId and m.deletedAt is null order by m.id")
    List<PaymentMethod> findAllActiveByUserId(@Param("userId") Long userId);

    /** 등록·기본 카드 변경 시 기존 카드들을 잠가 변경을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from PaymentMethod m where m.userId = :userId and m.deletedAt is null order by m.id")
    List<PaymentMethod> findAllActiveByUserIdForUpdate(@Param("userId") Long userId);
}
