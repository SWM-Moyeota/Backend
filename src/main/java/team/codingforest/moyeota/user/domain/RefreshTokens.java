package team.codingforest.moyeota.user.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokens {
    void save(RefreshToken token);
    Optional<RefreshToken> findByJti(UUID jti);
    Optional<Long> consume(UUID jti, Instant now);
}
