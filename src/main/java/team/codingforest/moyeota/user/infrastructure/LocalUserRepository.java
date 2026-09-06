package team.codingforest.moyeota.user.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LocalUserRepository extends JpaRepository<LocalUserEntity, Long> {
    @Query("select u from LocalUserEntity u where u.loginId = :loginId")
    Optional<LocalUserEntity> findByLoginId(@Param("loginId") String loginId);

    boolean existsByLoginId(String loginId);
}
