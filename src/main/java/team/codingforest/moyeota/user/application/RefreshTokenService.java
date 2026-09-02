package team.codingforest.moyeota.user.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.application.dto.TokenResponse;
import team.codingforest.moyeota.user.application.dto.TokenClaims;
import team.codingforest.moyeota.user.application.dto.TokenPair;
import team.codingforest.moyeota.user.domain.IdGenerator;
import team.codingforest.moyeota.user.domain.RefreshToken;
import team.codingforest.moyeota.user.domain.RefreshTokens;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;
import team.codingforest.moyeota.user.infrastructure.JwtProvider;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenService {
    private final JwtProvider jwtProvider;
    private final RefreshTokens refreshTokens;
    private final IdGenerator idGenerator;

    @Transactional
    public TokenResponse issue(Long userId, UUID publicId) {
        return issuePair(userId, publicId);
    }

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        TokenClaims claims = jwtProvider.parseRefresh(refreshToken);

        Optional<Long> consumed = refreshTokens.consume(claims.jti(), Instant.now());

        if (consumed.isEmpty()) {
            log.warn("refresh 토큰 소비 실패 jti={}", claims.jti());
            throw new UserException(UserErrorCode.TOKEN_INVALID);
        }

        Long userId = consumed.get();

        return issuePair(userId, claims.publicId());
    }

    @Transactional
    public void logout(String refreshToken) {
        TokenClaims claims = jwtProvider.parseRefresh(refreshToken);

        refreshTokens.consume(claims.jti(), Instant.now());
    }

    private TokenResponse issuePair(Long userId, UUID publicId) {
        UUID jti = idGenerator.generate(); // v7
        Instant now = Instant.now();

        TokenPair pair = jwtProvider.issuePair(publicId, jti, now);

        refreshTokens.save(RefreshToken.create(jti, userId, pair.refreshExpiresAt(), now));

        return new TokenResponse(pair.access(), pair.refresh());
    }
}
