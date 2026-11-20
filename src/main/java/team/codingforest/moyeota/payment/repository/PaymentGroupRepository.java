package team.codingforest.moyeota.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.payment.entity.PaymentGroup;

import java.util.Optional;

public interface PaymentGroupRepository extends JpaRepository<PaymentGroup, Long> {
    Optional<PaymentGroup> findByMatchId(Long matchId);
}
