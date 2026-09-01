package team.codingforest.moyeota.user.infrastructure;

import jakarta.persistence.Entity;
import lombok.Getter;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.user.domain.RefreshToken;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
public class RefreshTokenEntity extends BaseTimeEntity {

    private Long userId;

    private UUID publicId;

    private String refreshToken;

    private Instant expiredAt;

    private RefreshTokenEntity(Long userId, UUID publicId, String refreshToken, Instant expiredAt) {
        this.userId = userId;
        this.publicId = publicId;
        this.refreshToken = refreshToken;
        this.expiredAt = expiredAt;
    }

    public static RefreshTokenEntity from(RefreshToken token) {
        return new RefreshTokenEntity(token.getUserId(), token.getPublicId(), token.getRefreshToken(), token.getExpiredAt());
    }

    public RefreshToken toDomain() {
        return RefreshToken.from(getId(), userId, publicId, refreshToken, expiredAt, getCreatedAt(), getUpdatedAt());
    }
}
