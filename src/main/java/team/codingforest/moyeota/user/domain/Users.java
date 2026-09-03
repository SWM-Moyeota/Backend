package team.codingforest.moyeota.user.domain;

import java.util.Optional;
import java.util.UUID;

public interface Users {
    User save(User user);
    User findById(Long id);
    /** access 토큰의 publicId 로 내부 userId 를 찾기 위해 추가 */
    Optional<User> findByPublicId(UUID publicId);
}
