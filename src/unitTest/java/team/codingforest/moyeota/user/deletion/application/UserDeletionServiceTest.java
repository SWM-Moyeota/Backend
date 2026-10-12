package team.codingforest.moyeota.user.deletion.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.user.api.UserDeletedEvent;
import team.codingforest.moyeota.user.auth.domain.PrincipalCache;
import team.codingforest.moyeota.user.auth.domain.RefreshToken;
import team.codingforest.moyeota.user.auth.domain.RefreshTokens;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.deletion.domain.UserDeletions;
import team.codingforest.moyeota.user.local.domain.LocalUser;
import team.codingforest.moyeota.user.local.domain.LocalUsers;
import team.codingforest.moyeota.user.local.domain.PasswordHasher;
import team.codingforest.moyeota.user.profile.domain.UserProfile;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;
import team.codingforest.moyeota.user.users.application.FakeUsers;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.enums.Gender;
import team.codingforest.moyeota.user.users.domain.enums.LoginType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserDeletionServiceTest {
    private static final String PASSWORD = "password1!";

    private FakeUsers users;
    private FakeLocalUsers localUsers;
    private FakeUserProfiles userProfiles;
    private FakeUserDeletions userDeletions;
    private FakeRefreshTokens refreshTokens;
    private FakePrincipalCache principalCache;
    private Set<Long> inOngoingParty;
    private List<Object> events;
    private UserDeletionService service;
    private User user;

    @BeforeEach
    void setUp() {
        users = new FakeUsers();
        localUsers = new FakeLocalUsers();
        userProfiles = new FakeUserProfiles();
        userDeletions = new FakeUserDeletions();
        refreshTokens = new FakeRefreshTokens();
        principalCache = new FakePrincipalCache();
        inOngoingParty = new HashSet<>();
        events = new ArrayList<>();
        AfterCommitExecutor runNow = (name, task) -> task.run();   // 커밋 후 작업은 바로 실행해 결과만 본다

        service = new UserDeletionService(users, localUsers, userProfiles, userDeletions, refreshTokens, principalCache,
                new PrefixPasswordHasher(), inOngoingParty::contains, events::add, runNow);

        user = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL, "모여타"));
        localUsers.register(user.getId(), "moyeota", "hashed:" + PASSWORD);
        userProfiles.save(UserProfile.of(user.getId(), "홍길동", Instant.now(), "010-1234-5678", Gender.MALE, "a@b.com"));
        principalCache.save(user.getPublicId(), user.getId());
    }

    @Test
    void 탈퇴하면_개인정보를_지우고_번호_해시만_남긴다() {
        service.delete(user.getId(), PASSWORD);

        User deleted = users.findById(user.getId());
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getNickname()).as("unique 라 비워야 다른 사람이 쓸 수 있다").isNull();
        assertThat(userProfiles.findByUserId(user.getId())).isEmpty();
        assertThat(localUsers.findByUserId(user.getId())).isEmpty();
        assertThat(userDeletions.deletedAt).containsKey("01012345678");   // 정규화된 번호로 남겨야 SMS 체크와 맞는다
    }

    @Test
    void 탈퇴하면_토큰과_인증_캐시를_무효화하고_이벤트를_낸다() {
        service.delete(user.getId(), PASSWORD);

        assertThat(refreshTokens.cancelledUserIds).containsExactly(user.getId());
        assertThat(principalCache.store).doesNotContainKey(user.getPublicId());
        assertThat(events).containsExactly(new UserDeletedEvent(user.getId(), user.getPublicId()));
    }

    @Test
    void 비밀번호가_틀리면_아무것도_바꾸지_않는다() {
        assertError(() -> service.delete(user.getId(), "wrong"), UserErrorCode.PASSWORD_MISMATCH);

        assertNothingChanged();
    }

    @Test
    void 진행_중인_합승이_있으면_탈퇴할_수_없다() {
        inOngoingParty.add(user.getId());

        assertError(() -> service.delete(user.getId(), PASSWORD), UserErrorCode.ONGOING_PARTY_EXISTS);

        assertNothingChanged();
    }

    @Test
    void 로컬_계정이_없으면_본인_확인을_못_해서_거절한다() {
        // 소셜 가입자 - 비밀번호가 없어 아직 탈퇴 경로가 없다
        User social = users.save(User.from(UUID.randomUUID(), LoginType.SOCIAL));

        assertError(() -> service.delete(social.getId(), PASSWORD), UserErrorCode.PASSWORD_MISMATCH);
        assertThat(users.findById(social.getId()).isDeleted()).isFalse();
    }

    private void assertNothingChanged() {
        assertThat(users.findById(user.getId()).isDeleted()).isFalse();
        assertThat(userProfiles.findByUserId(user.getId())).isPresent();
        assertThat(localUsers.findByUserId(user.getId())).isPresent();
        assertThat(userDeletions.deletedAt).isEmpty();
        assertThat(refreshTokens.cancelledUserIds).isEmpty();
        assertThat(events).isEmpty();
    }

    private static void assertError(Runnable call, UserErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    // ───────────────────────── 페이크 ─────────────────────────

    static class PrefixPasswordHasher implements PasswordHasher {
        @Override
        public String hash(String password) { return "hashed:" + password; }

        @Override
        public boolean matches(String rawPassword, String hashed) { return hash(rawPassword).equals(hashed); }
    }

    static class FakeLocalUsers implements LocalUsers {
        final Map<Long, LocalUser> byUserId = new HashMap<>();

        @Override
        public void register(Long userId, String loginId, String password) {
            byUserId.put(userId, LocalUser.from(userId, loginId, password, Instant.now(), Instant.now()));
        }

        @Override
        public LocalUser findByLoginId(String loginId) {
            return byUserId.values().stream().filter(u -> u.getLoginId().equals(loginId)).findFirst()
                    .orElseThrow(() -> new UserException(UserErrorCode.LOGIN_FAILED));
        }

        @Override
        public boolean existsByLoginId(String loginId) {
            return byUserId.values().stream().anyMatch(u -> u.getLoginId().equals(loginId));
        }

        @Override
        public Optional<LocalUser> findByUserId(Long userId) { return Optional.ofNullable(byUserId.get(userId)); }

        @Override
        public void deleteByUserId(Long userId) { byUserId.remove(userId); }
    }

    static class FakeUserProfiles implements UserProfiles {
        final Map<Long, UserProfile> byUserId = new HashMap<>();

        @Override
        public void save(UserProfile profile) { byUserId.put(profile.getUserId(), profile); }

        @Override
        public Optional<UserProfile> findByUserId(Long userId) { return Optional.ofNullable(byUserId.get(userId)); }

        @Override
        public boolean existsByPhoneNumber(String phone) {
            return byUserId.values().stream().anyMatch(p -> p.getPhoneNumber().equals(phone));
        }

        @Override
        public void deleteByUserId(Long userId) { byUserId.remove(userId); }
    }

    static class FakeUserDeletions implements UserDeletions {
        final Map<String, Instant> deletedAt = new HashMap<>();

        @Override
        public void record(String normalizedPhoneNumber, Instant at) { deletedAt.put(normalizedPhoneNumber, at); }

        @Override
        public boolean existsDeletedSince(String normalizedPhoneNumber, Instant since) {
            Instant at = deletedAt.get(normalizedPhoneNumber);
            return at != null && at.isAfter(since);
        }
    }

    static class FakeRefreshTokens implements RefreshTokens {
        final List<Long> cancelledUserIds = new ArrayList<>();

        @Override
        public void save(RefreshToken token) { }

        @Override
        public Optional<RefreshToken> findByJti(UUID jti) { return Optional.empty(); }

        @Override
        public Optional<Long> consume(UUID jti, Instant now) { return Optional.empty(); }

        @Override
        public void cancelAllByUserId(Long userId, Instant now) { cancelledUserIds.add(userId); }
    }

    static class FakePrincipalCache implements PrincipalCache {
        final Map<UUID, Long> store = new HashMap<>();

        @Override
        public Optional<Long> findUserId(UUID publicId) { return Optional.ofNullable(store.get(publicId)); }

        @Override
        public void save(UUID publicId, Long userId) { store.put(publicId, userId); }

        @Override
        public void evict(UUID publicId) { store.remove(publicId); }
    }
}
