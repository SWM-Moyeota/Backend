package team.codingforest.moyeota.user.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.user.domain.RefreshToken;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "refresh_token")
public class RefreshTokenEntity {
    @Id
    private UUID jti;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant cancelledAt;

    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    private RefreshTokenEntity(UUID jti, Long userId, Instant expiresAt, Instant cancelledAt, Instant createdAt, Instant updatedAt) {
        this.jti = jti;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.cancelledAt = cancelledAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static RefreshTokenEntity from(RefreshToken token) {
        return new RefreshTokenEntity(
                token.getJti(), token.getUserId(), token.getExpiresAt(), token.getCancelledAt(), token.getCreatedAt(), token.getUpdatedAt());
    }

    public RefreshToken toDomain() {
        return RefreshToken.of(jti, userId, expiresAt, cancelledAt, createdAt, updatedAt);
    }
}