package team.codingforest.moyeota.user.deletion.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface UserDeletionRepository extends JpaRepository<UserDeletionEntity, String> {
    boolean existsByPhoneHashAndDeletedAtAfter(String phoneHash, Instant since);
}
