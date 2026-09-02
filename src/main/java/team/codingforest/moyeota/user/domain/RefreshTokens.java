package team.codingforest.moyeota.user.domain.enums;

import team.codingforest.moyeota.user.domain.RefreshToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokens {
    void save(RefreshToken token);
    Optional<RefreshToken> findByJti(UUID jti);
    Optional<Long> consume(UUID jti, Instant now);
}
