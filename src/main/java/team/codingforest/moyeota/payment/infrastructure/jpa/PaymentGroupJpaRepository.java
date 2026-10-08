package team.codingforest.moyeota.payment.infrastructure.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentGroupJpaRepository extends JpaRepository<PaymentGroupEntity, Long> {

    /** 잔액·상태를 같이 바꾸는 모든 전이는 그룹 행을 잠근 뒤 한다 (웹훅 vs 재시도 경합) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from PaymentGroupEntity g where g.id = :id")
    Optional<PaymentGroupEntity> findByIdForUpdate(@Param("id") Long id);

    @Query("select g from PaymentGroupEntity g where g.partyId = :partyId")
    Optional<PaymentGroupEntity> findByPartyId(@Param("partyId") Long partyId);

    @Query("select distinct g from PaymentGroupEntity g join g.payments p where p.userId = :userId order by g.id desc")
    List<PaymentGroupEntity> findAllByUserId(@Param("userId") Long userId);
}
