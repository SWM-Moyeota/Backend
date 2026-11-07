package team.codingforest.moyeota.user.users.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByPublicId(UUID publicId);
    List<UserEntity> findAllByIdInAndFcmTokenIsNotNull(Collection<Long> ids);
    boolean existsByNickname(String nickname);

    // 노쇼 제재를 바꿀 때 - 같은 사용자의 노쇼 두 건이 동시에 기록돼도 서로를 세게 행을 잠근다
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") Long id);
}
