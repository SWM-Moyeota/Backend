package team.codingforest.moyeota.user.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class RefreshToken {
    private final UUID jti;
    private final Long userId;
    private final Instant expiresAt;
    private final Instant cancelledAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private RefreshToken(UUID jti, Long userId, Instant expiresAt, Instant cancelledAt, Instant createdAt, Instant updatedAt) {
        this.jti = jti;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.cancelledAt = cancelledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static RefreshToken of(UUID jti, Long userId, Instant expiresAt, Instant cancelledAt, Instant createdAt, Instant updatedAt) {
        return new RefreshToken(jti, userId, expiresAt, cancelledAt, createdAt, updatedAt);
    }

    public static RefreshToken create(UUID jti, Long userId, Instant expiresAt, Instant now) {
        return new RefreshToken(jti, userId, expiresAt, null, now, now);
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    public boolean isUsable(Instant now) {
        return !isCancelled() && expiresAt.isAfter(now);
    }
}
