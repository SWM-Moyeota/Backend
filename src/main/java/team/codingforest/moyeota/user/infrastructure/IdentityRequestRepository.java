package team.codingforest.moyeota.user.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IdentityRequestRepository extends JpaRepository<IdentityRequestEntity, String> {
    Optional<IdentityRequestEntity> findByIdAndUserId(String id, Long userId);
    Optional<IdentityRequestEntity> findFirstByUserIdOrderByCreatedAtDesc(Long userId);
}
