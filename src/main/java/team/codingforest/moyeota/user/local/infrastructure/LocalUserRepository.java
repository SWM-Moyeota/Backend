package team.codingforest.moyeota.user.local.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LocalUserRepository extends JpaRepository<LocalUserEntity, Long> {
    @Query("select u from LocalUserEntity u where u.loginId = :loginId")
    Optional<LocalUserEntity> findByLoginId(@Param("loginId") String loginId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from LocalUserEntity u where u.id = :id")
    Optional<LocalUserEntity> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from LocalUserEntity u where u.loginId = :loginId")
    Optional<LocalUserEntity> findByLoginIdForUpdate(@Param("loginId") String loginId);

    boolean existsByLoginId(String loginId);
}
