package team.codingforest.moyeota.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.payment.entity.PaymentMethod;

import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<PaymentMethod, Long> {
    //Optional<PaymentMethod> findByMethodId(Long methodId);
}
