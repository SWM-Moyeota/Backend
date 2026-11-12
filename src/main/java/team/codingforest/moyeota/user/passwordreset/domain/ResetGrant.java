package team.codingforest.moyeota.user.passwordreset.domain;

import java.time.Instant;
import java.util.UUID;

public record ResetGrant(UUID id, String tokenHash, Long userId, String phoneHash, long generation, Instant expiresAt, Instant consumedAt, Instant createdAt) {
public boolean usableAt(Instant now) { return consumedAt == null && expiresAt.isAfter(now); }
@Override public String toString() { return "ResetGrant[보안 정보 생략]"; }
}
