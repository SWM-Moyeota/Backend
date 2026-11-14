package team.codingforest.moyeota.user.auth.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.auth.domain.RefreshToken;
import team.codingforest.moyeota.user.auth.domain.RefreshTokens;
import team.codingforest.moyeota.user.auth.dto.TokenClaims;
import team.codingforest.moyeota.user.auth.dto.TokenPair;
import team.codingforest.moyeota.user.auth.dto.TokenResponse;
import team.codingforest.moyeota.user.auth.infrastructure.JwtProvider;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.users.domain.IdGenerator;
import team.codingforest.moyeota.user.local.domain.LocalUsers;
import team.codingforest.moyeota.user.social.domain.SocialUsers;

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
    private final LocalUsers localUsers;
    private final SocialUsers socialUsers;
    private final team.codingforest.moyeota.user.users.domain.Users users;

    @Transactional
    public TokenResponse issue(Long userId, UUID publicId) {
        if (users.findByIdForUpdate(userId).isDeleted()) throw new UserException(UserErrorCode.TOKEN_INVALID);
        return issuePair(userId, publicId);
    }

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        TokenClaims claims = jwtProvider.parseRefresh(refreshToken);

        Long ownerId = refreshTokens.findByJti(claims.jti())
                .orElseThrow(() -> new UserException(UserErrorCode.TOKEN_INVALID)).getUserId();
        if (users.findByIdForUpdate(ownerId).isDeleted()) throw new UserException(UserErrorCode.TOKEN_INVALID);
        // 로그인 수단 행을 잠근다. 재설정·탈퇴와 같은 잠금 순서를 사용한다.
        // 로컬 회원은 local_user, 소셜 회원은 social_user 행이다. 둘 다 없으면 탈퇴 등으로 로그인 수단이 사라진 것이다
        if (localUsers.findByUserIdForUpdate(ownerId).isEmpty() && socialUsers.findByUserIdForUpdate(ownerId).isEmpty()) {
            throw new UserException(UserErrorCode.TOKEN_INVALID);
        }
        Optional<Long> consumed = refreshTokens.consume(claims.jti(), Instant.now());

        if (consumed.isEmpty()) {
            log.warn("refresh 토큰 소비 실패 jti={}", claims.jti());
            throw new UserException(UserErrorCode.TOKEN_INVALID);
        }

        Long userId = consumed.get();

        return issuePair(userId, claims.publicId());
    }

    @Transactional   // consume 이 @Modifying UPDATE 라 트랜잭션 필수 - 없으면 TransactionRequiredException
    public void logout(String refreshToken) {
        try {
            TokenClaims claims = jwtProvider.parseRefresh(refreshToken);
            refreshTokens.consume(claims.jti(), Instant.now());
        } catch (UserException e) {
            log.info("로그아웃 - 이미 무효한 리프레시, 처리할 것 없음");
        }
    }

    private TokenResponse issuePair(Long userId, UUID publicId) {
        UUID jti = idGenerator.generate(); // v7
        Instant now = Instant.now();

        TokenPair pair = jwtProvider.issuePair(publicId, jti, now);

        refreshTokens.save(RefreshToken.create(jti, userId, pair.refreshExpiresAt(), now));

        return new TokenResponse(pair.access(), pair.refresh());
    }
}
