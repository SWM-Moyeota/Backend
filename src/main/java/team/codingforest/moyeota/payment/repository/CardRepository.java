package team.codingforest.moyeota.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.payment.domain.entity.Card;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {
}
