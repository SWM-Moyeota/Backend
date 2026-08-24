package team.codingforest.moyeota.auth.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.auth.entity.RefreshToken;

//PK가 user_id이므로 findById(userId)로 그 사용자의 토큰 행을 바로 찾을 수 있다.
public interface RefreshRepository extends JpaRepository<RefreshToken, Long> {

    Boolean existsByRefreshToken(String refreshToken);

    @Transactional
    void deleteByRefreshToken(String refreshToken);
}
