package team.codingforest.moyeota.user.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.domain.RefreshToken;
import team.codingforest.moyeota.user.domain.enums.RefreshTokens;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RefreshTokenJpa implements RefreshTokens {
    private final RefreshTokenRepository delegate;

    @Override
    public void save(RefreshToken token) {
        RefreshTokenEntity entity = RefreshTokenEntity.from(token);
        delegate.save(entity);
    }

    @Override
    public Optional<RefreshToken> findByJti(UUID jti) {
        return delegate.findByJti(jti).map(RefreshTokenEntity::toDomain);
    }
}
