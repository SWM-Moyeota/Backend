package team.codingforest.moyeota.user.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public interface IdentityVerifications {
    IdentityRequest start(Long userId, String storeId, String channelKey, Instant now, Duration validity);
    IdentityRequest findOwned(String requestId, Long userId);
    Optional<VerifiedIdentity> findVerified(Long userId);
    VerifiedIdentity complete(IdentityRequest request, String diHash, Instant verifiedAt, Instant now);
    boolean existsByDiHash(String diHash);
}
