package team.codingforest.moyeota.user.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface Users {
    User save(User user);
    User findById(Long id);
    Optional<User> findByPublicId(UUID publicId);
    Map<Long, String> findFcmTokens(List<Long> userIds);
    List<User> findAllByIds(List<Long> ids);
    boolean existsByNickname(String nickname);
}
