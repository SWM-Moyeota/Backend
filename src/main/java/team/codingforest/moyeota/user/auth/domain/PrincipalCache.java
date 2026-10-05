package team.codingforest.moyeota.user.auth.domain;

import java.util.Optional;
import java.util.UUID;

public interface PrincipalCache {
    Optional<Long> findUserId(UUID publicId);
    void save(UUID publicId, Long userId);
}
