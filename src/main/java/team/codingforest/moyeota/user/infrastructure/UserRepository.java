package team.codingforest.moyeota.user.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByPublicId(UUID publicId);
    List<UserEntity> findAllByIdInAndFcmTokenIsNotNull(Collection<Long> ids);
}
