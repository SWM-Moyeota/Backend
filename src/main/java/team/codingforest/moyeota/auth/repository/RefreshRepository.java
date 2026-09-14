package team.codingforest.moyeota.auth.repository;

import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import team.codingforest.moyeota.auth.entity.RefreshToken;

import java.util.Optional;

public interface RefreshRepository extends JpaRepository<RefreshToken, Long> {

    //동일한 refresh로 재발급 요청이 동시에 들어와 둘 다 성공하지 않도록 해당 행을 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rt from RefreshToken rt where rt.refreshToken = :refreshToken")
    Optional<RefreshToken> findByRefreshTokenForUpdate(@Param("refreshToken") String refreshToken);

    @Transactional
    void deleteByRefreshToken(String refreshToken);
}
