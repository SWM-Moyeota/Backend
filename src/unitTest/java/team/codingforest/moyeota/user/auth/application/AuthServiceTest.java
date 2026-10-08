package team.codingforest.moyeota.user.auth.application;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import team.codingforest.moyeota.user.auth.domain.PrincipalCache;
import team.codingforest.moyeota.user.auth.infrastructure.JwtProvider;
import team.codingforest.moyeota.user.common.application.FakeUsers;
import team.codingforest.moyeota.user.common.domain.User;
import team.codingforest.moyeota.user.common.domain.enums.LoginType;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 *  매 요청 인증(authenticate). 토큰 검증은 매번 하고, 공개 ID → 내부 번호 변환만 캐시를 거친다.
 *  이 조회가 요청마다 DB 로 가면 커넥션 풀이 붐빌 때 모든 요청이 서비스 진입 전에 한 번 더 줄을 선다.
 */
class AuthServiceTest {
    private static final String SECRET = "deXiPUTSbvL2Qg673qn22K4S6fjf6VSBMLfW54E16DrUPty9aapfs/PGqoO+NdP9";

    private final JwtProvider jwtProvider = new JwtProvider(SECRET, Duration.ofMinutes(30), Duration.ofDays(14));
    private final CountingUsers users = new CountingUsers();
    private final MemoryPrincipalCache cache = new MemoryPrincipalCache();
    // 로그인·재발급 협력자는 authenticate 가 쓰지 않는다
    private final AuthService service = new AuthService(null, null, jwtProvider, users, cache);

    @Test
    void 캐시에_없으면_DB에서_찾고_캐시에_적어_둔다() {
        User user = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL));

        AuthenticatedPrincipal principal = service.authenticate(accessTokenOf(user.getPublicId()));

        assertThat(principal).isEqualTo(new AuthenticatedPrincipal(user.getId(), user.getPublicId()));
        assertThat(users.lookups).isEqualTo(1);
        assertThat(cache.store).containsEntry(user.getPublicId(), user.getId());
    }

    @Test
    void 캐시에_있으면_DB를_조회하지_않는다() {
        User user = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL));
        String token = accessTokenOf(user.getPublicId());
        service.authenticate(token);

        AuthenticatedPrincipal second = service.authenticate(token);

        assertThat(second.userId()).isEqualTo(user.getId());
        assertThat(users.lookups).as("두 번째 요청부터는 DB 로 가지 않는다").isEqualTo(1);
    }

    @Test
    void 캐시가_고장_나_아무것도_못_돌려줘도_DB로_인증된다() {
        User user = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL));
        cache.broken = true;   // 구현은 예외 대신 빈 값을 돌려주고 저장을 무시한다
        String token = accessTokenOf(user.getPublicId());

        service.authenticate(token);
        AuthenticatedPrincipal principal = service.authenticate(token);

        assertThat(principal.userId()).isEqualTo(user.getId());
        assertThat(users.lookups).isEqualTo(2);
    }

    @Test
    void 없는_사용자의_토큰은_거부하고_캐시에_남기지_않는다() {
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> service.authenticate(accessTokenOf(unknown)))
                .isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.TOKEN_INVALID);
        assertThat(cache.store).isEmpty();
    }

    @Test
    void 캐시에_있는_사용자라도_토큰_검증은_매번_한다() {
        User user = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL));
        service.authenticate(accessTokenOf(user.getPublicId()));   // 캐시에 올린다
        String old = jwtProvider.issuePair(user.getPublicId(), UUID.randomUUID(), Instant.now().minus(Duration.ofHours(1))).access();

        assertThatThrownBy(() -> service.authenticate(old))
                .isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.TOKEN_EXPIRED);
        assertThatThrownBy(() -> service.authenticate("위조된.토큰.값"))
                .isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.TOKEN_INVALID);
    }

    @Test
    void refresh_토큰으로는_인증할_수_없다() {
        User user = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL));
        String refresh = jwtProvider.issuePair(user.getPublicId(), UUID.randomUUID(), Instant.now()).refresh();

        assertThatThrownBy(() -> service.authenticate(refresh))
                .isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.TOKEN_INVALID);
        assertThat(users.lookups).isZero();
    }

    /** 누가 습관적으로 다시 붙이면 캐시 적중에도 커넥션을 얻어 이 변경의 효과가 조용히 사라진다 */
    @Test
    void authenticate_에는_트랜잭션을_걸지_않는다() throws NoSuchMethodException {
        assertThat(AuthService.class.getMethod("authenticate", String.class).isAnnotationPresent(Transactional.class)).isFalse();
        assertThat(AuthService.class.isAnnotationPresent(Transactional.class)).isFalse();
    }

    private String accessTokenOf(UUID publicId) {
        return jwtProvider.issuePair(publicId, UUID.randomUUID(), Instant.now()).access();
    }

    private static class CountingUsers extends FakeUsers {
        int lookups = 0;

        @Override
        public Optional<User> findByPublicId(UUID publicId) {
            lookups++;
            return super.findByPublicId(publicId);
        }
    }

    private static class MemoryPrincipalCache implements PrincipalCache {
        final Map<UUID, Long> store = new HashMap<>();
        boolean broken = false;

        @Override
        public Optional<Long> findUserId(UUID publicId) {
            return broken ? Optional.empty() : Optional.ofNullable(store.get(publicId));
        }

        @Override
        public void save(UUID publicId, Long userId) {
            if(!broken) store.put(publicId, userId);
        }
    }
}
