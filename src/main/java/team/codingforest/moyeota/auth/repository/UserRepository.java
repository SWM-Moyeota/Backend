package team.codingforest.moyeota.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import team.codingforest.moyeota.auth.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {

    //JWT의 subject로 publicId를 쓰므로, 토큰에서 사용자를 찾을 때 이 메서드를 쓴다.
    Optional<User> findByPublicId(UUID publicId);
}
