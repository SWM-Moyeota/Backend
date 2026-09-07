package team.codingforest.moyeota.auth.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.auth.entity.RefreshToken;

import java.util.Optional;

//PK가 이 행의 자체 id로 바뀌었으므로 findById(userId)로는 더 이상 찾을 수 없다.
//사용자의 토큰 행을 찾을 때는 아래 findByUserUserId를 쓴다.
public interface RefreshRepository extends JpaRepository<RefreshToken, Long> {

    /*
    그 사용자의 refresh 행을 찾는다. user_id에 unique 제약이 있어 최대 하나다.
    (제약을 떼고 기기별 로그인을 허용하게 되면 이 메서드는 List를 반환하도록 바꿔야 한다.
     Optional인 채로 두면 행이 둘 이상일 때 NonUniqueResultException이 난다)
    */
    Optional<RefreshToken> findByUserUserId(Long userId);

    Boolean existsByRefreshToken(String refreshToken);

    @Transactional
    void deleteByRefreshToken(String refreshToken);
}
