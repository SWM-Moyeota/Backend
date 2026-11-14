package team.codingforest.moyeota.user.auth.application;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.user.auth.domain.RefreshToken;
import team.codingforest.moyeota.user.auth.domain.RefreshTokens;
import team.codingforest.moyeota.user.auth.dto.TokenResponse;
import team.codingforest.moyeota.user.auth.infrastructure.JwtProvider;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.local.domain.LocalUser;
import team.codingforest.moyeota.user.local.domain.LocalUsers;
import team.codingforest.moyeota.user.social.FakeSocialUsers;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 *  재발급은 로그인 수단 행을 잠그고 진행한다. 로컬 회원은 local_user, 소셜 회원은 social_user 행이다.
 *  local_user 만 보면 소셜 회원은 access 토큰이 만료될 때마다 다시 로그인해야 한다
 */
class RefreshTokenServiceTest {
    private static final String SECRET = "deXiPUTSbvL2Qg673qn22K4S6fjf6VSBMLfW54E16DrUPty9aapfs/PGqoO+NdP9";
    private static final Long USER_ID = 7L;
    private static final UUID PUBLIC_ID = UUID.randomUUID();

    private final JwtProvider jwtProvider = new JwtProvider(SECRET, Duration.ofMinutes(30), Duration.ofDays(14));
    private final MemoryRefreshTokens refreshTokens = new MemoryRefreshTokens();
    private final LocalUsers localUsers = mock(LocalUsers.class);   // 기본은 로컬 계정 없음(Optional.empty)
    private final FakeSocialUsers socialUsers = new FakeSocialUsers();
    private final RefreshTokenService service = new RefreshTokenService(jwtProvider, refreshTokens, UUID::randomUUID, localUsers, socialUsers);

    @Test
    void 로컬_회원은_재발급된다() {
        given(localUsers.findByUserIdForUpdate(USER_ID))
                .willReturn(Optional.of(LocalUser.from(USER_ID, "hong", "hashed", Instant.now(), Instant.now())));
        TokenResponse issued = service.issue(USER_ID, PUBLIC_ID);

        TokenResponse reissued = service.reissue(issued.refreshToken());

        assertThat(jwtProvider.parseAccess(reissued.accessToken())).isEqualTo(PUBLIC_ID);
    }

    @Test
    void 소셜_회원도_재발급된다() {
        socialUsers.register(USER_ID, SocialType.GOOGLE, "google-sub-1");
        TokenResponse issued = service.issue(USER_ID, PUBLIC_ID);

        TokenResponse reissued = service.reissue(issued.refreshToken());

        assertThat(jwtProvider.parseAccess(reissued.accessToken())).isEqualTo(PUBLIC_ID);
        assertThat(reissued.refreshToken()).isNotEqualTo(issued.refreshToken());
    }

    @Test
    void 재발급에_쓴_refresh_는_다시_쓸_수_없다() {
        socialUsers.register(USER_ID, SocialType.GOOGLE, "google-sub-1");
        TokenResponse issued = service.issue(USER_ID, PUBLIC_ID);
        service.reissue(issued.refreshToken());

        assertInvalid(issued.refreshToken());
    }

    @Test
    void 로그인_수단이_하나도_없으면_재발급을_거절한다() {
        TokenResponse issued = service.issue(USER_ID, PUBLIC_ID);

        assertInvalid(issued.refreshToken());
    }

    private void assertInvalid(String refreshToken) {
        assertThatThrownBy(() -> service.reissue(refreshToken))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.TOKEN_INVALID);
    }

    static class MemoryRefreshTokens implements RefreshTokens {
        private final Map<UUID, RefreshToken> store = new HashMap<>();

        @Override
        public void save(RefreshToken token) { store.put(token.getJti(), token); }

        @Override
        public Optional<RefreshToken> findByJti(UUID jti) { return Optional.ofNullable(store.get(jti)); }

        @Override
        public Optional<Long> consume(UUID jti, Instant now) {
            RefreshToken token = store.get(jti);
            if (token == null || !token.isUsable(now)) return Optional.empty();
            store.put(jti, RefreshToken.of(jti, token.getUserId(), token.getExpiresAt(), now, token.getCreatedAt(), now));
            return Optional.of(token.getUserId());
        }

        @Override
        public void cancelAllByUserId(Long userId, Instant now) {
            store.replaceAll((jti, t) -> t.getUserId().equals(userId)
                    ? RefreshToken.of(jti, userId, t.getExpiresAt(), now, t.getCreatedAt(), now) : t);
        }
    }
}
