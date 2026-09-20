package team.codingforest.moyeota.user.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForMatching(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<UserEntity> findByPublicId(UUID publicId);
    List<UserEntity> findAllByIdInAndFcmTokenIsNotNull(Collection<Long> ids);
    boolean existsByNickname(String nickname);
}
