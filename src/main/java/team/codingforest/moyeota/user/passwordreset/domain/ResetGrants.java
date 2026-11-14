package team.codingforest.moyeota.user.passwordreset.domain;

import java.util.Optional;
import java.time.Instant;

public interface ResetGrants {
 void save(ResetGrant grant);
 Optional<Long> findOwnerId(String hash);
 Optional<ResetGrant> lockByTokenHash(String hash);
 void consumeAll(Long userId, Instant now);
}
