package team.codingforest.moyeota.user.domain;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public class RefreshToken {
    private final Long id;
    private final Long userId;
    private final UUID publicId;
    private final String refreshToken;
    private final Instant expiredAt;
    private final Instant createdAt;
    private final Instant updatedAt;

    private RefreshToken(Long id, Long userId, UUID publicId, String refreshToken, Instant expiredAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.publicId = publicId;
        this.refreshToken = refreshToken;
        this.createdAt = createdAt;
        this.expiredAt = expiredAt;
        this.updatedAt = updatedAt;
    }

    public static RefreshToken from(Long id, Long userId, UUID publicId, String refreshToken, Instant expiredAt, Instant createdAt, Instant updatedAt) {
        return new RefreshToken(id, userId, publicId, refreshToken, expiredAt, createdAt, updatedAt);
    }

    public static RefreshToken create(Long userId, UUID publicId, String refreshToken, Instant expiredAt) {
        return new RefreshToken(null, userId, publicId, refreshToken, expiredAt, Instant.now(), Instant.now()); }
}
