package team.codingforest.moyeota.user.social.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.user.auth.application.RefreshTokenService;
import team.codingforest.moyeota.user.auth.dto.TokenResponse;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.profile.domain.UserProfile;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;
import team.codingforest.moyeota.user.social.FakeSocialUsers;
import team.codingforest.moyeota.user.social.domain.SocialProfile;
import team.codingforest.moyeota.user.social.domain.SocialSignupTickets;
import team.codingforest.moyeota.user.social.domain.SocialUser;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;
import team.codingforest.moyeota.user.social.dto.SocialLoginResponse;
import team.codingforest.moyeota.user.social.dto.SocialLoginStatus;
import team.codingforest.moyeota.user.social.dto.SocialRegisterCommand;
import team.codingforest.moyeota.user.users.application.FakeUsers;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.enums.Gender;
import team.codingforest.moyeota.user.users.domain.enums.LoginType;
import team.codingforest.moyeota.user.verification.application.SmsVerificationService;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class SocialAuthServiceTest {
    private static final String ID_TOKEN = "google-id-token";
    private static final SocialProfile 구글_홍길동 = new SocialProfile(SocialType.GOOGLE, "google-sub-1", "hong@gmail.com", "홍길동");
    private static final TokenResponse TOKENS = new TokenResponse("access", "refresh");

    private FakeUsers users;
    private FakeSocialUsers socialUsers;
    private FakeUserProfiles userProfiles;
    private FakeSignupTickets signupTickets;
    private Map<String, SocialProfile> googleAccounts;   // idToken → 구글이 확인해 준 사용자
    private SmsVerificationService smsVerificationService;
    private RefreshTokenService refreshTokenService;
    private SocialAuthService service;

    @BeforeEach
    void setUp() {
        users = new FakeUsers();
        socialUsers = new FakeSocialUsers();
        userProfiles = new FakeUserProfiles();
        signupTickets = new FakeSignupTickets();
        googleAccounts = new HashMap<>(Map.of(ID_TOKEN, 구글_홍길동));
        smsVerificationService = mock(SmsVerificationService.class);   // 기본은 인증 통과
        refreshTokenService = mock(RefreshTokenService.class);
        given(refreshTokenService.issue(any(), any())).willReturn(TOKENS);

        service = new SocialAuthService(
                idToken -> Optional.ofNullable(googleAccounts.get(idToken))
                        .orElseThrow(() -> new UserException(UserErrorCode.SOCIAL_TOKEN_INVALID)),
                socialUsers, signupTickets, users, userProfiles, UUID::randomUUID, smsVerificationService, refreshTokenService, org.mockito.Mockito.mock(team.codingforest.moyeota.user.social.apple.AppleCredentialStore.class));
    }

    private SocialRegisterCommand 가입(String signupToken, String nickname, String phone) {
        return new SocialRegisterCommand(signupToken, "sms-token", phone, nickname, "홍길동",
                Instant.parse("2000-01-01T00:00:00Z"), Gender.MALE);
    }

    private String 대기표() {
        return service.loginWithGoogle(ID_TOKEN).signupToken();
    }

    // ───────────────────────── 로그인 ─────────────────────────

    @Test
    void 처음_온_구글_계정이면_토큰_대신_가입_대기표를_준다() {
        SocialLoginResponse response = service.loginWithGoogle(ID_TOKEN);

        assertThat(response.status()).isEqualTo(SocialLoginStatus.SIGNUP_REQUIRED);
        assertThat(response.signupToken()).isNotBlank();
        assertThat(response.email()).isEqualTo("hong@gmail.com");
        assertThat(response.name()).isEqualTo("홍길동");
        assertThat(response.accessToken()).isNull();
        assertThat(response.refreshToken()).isNull();
        assertThat(signupTickets.ttls.get(response.signupToken())).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void 처음_온_사람은_로그인만으로_회원이_만들어지지_않는다() {
        service.loginWithGoogle(ID_TOKEN);

        assertThat(socialUsers.exists(SocialType.GOOGLE, "google-sub-1")).isFalse();
        then(refreshTokenService).should(never()).issue(any(), any());
    }

    @Test
    void 가입한_구글_계정이면_바로_토큰을_준다() {
        service.register(가입(대기표(), "길동이", "010-1234-5678"));

        SocialLoginResponse response = service.loginWithGoogle(ID_TOKEN);

        assertThat(response.status()).isEqualTo(SocialLoginStatus.LOGIN);
        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
        assertThat(response.signupToken()).isNull();
    }

    @Test
    void 검사에_실패한_idToken_은_거절한다() {
        assertError(() -> service.loginWithGoogle("forged"), UserErrorCode.SOCIAL_TOKEN_INVALID);

        assertThat(signupTickets.store).isEmpty();
    }

    // ───────────────────────── 가입 ─────────────────────────

    @Test
    void 가입하면_세_테이블에_저장하고_토큰을_준다() {
        TokenResponse tokens = service.register(가입(대기표(), "길동이", "010-1234-5678"));

        SocialUser socialUser = socialUsers.find(SocialType.GOOGLE, "google-sub-1").orElseThrow();
        User user = users.findById(socialUser.getUserId());
        UserProfile profile = userProfiles.findByUserId(user.getId()).orElseThrow();

        assertThat(tokens).isEqualTo(TOKENS);
        assertThat(user.getLoginType()).isEqualTo(LoginType.SOCIAL);
        assertThat(user.getNickname()).isEqualTo("길동이");
        assertThat(profile.getPhoneNumber()).isEqualTo("01012345678");
        assertThat(profile.getName()).isEqualTo("홍길동");
        then(refreshTokenService).should().issue(user.getId(), user.getPublicId());
    }

    @Test
    void 이메일은_구글이_확인해_준_값으로_저장한다() {
        service.register(가입(대기표(), "길동이", "010-1234-5678"));

        Long userId = socialUsers.find(SocialType.GOOGLE, "google-sub-1").orElseThrow().getUserId();
        assertThat(userProfiles.findByUserId(userId).orElseThrow().getEmail()).isEqualTo("hong@gmail.com");
    }

    @Test
    void 가입이_끝나면_대기표와_문자_인증_토큰을_지운다() {
        String signupToken = 대기표();

        service.register(가입(signupToken, "길동이", "010-1234-5678"));

        assertThat(signupTickets.store).doesNotContainKey(signupToken);
        then(smsVerificationService).should().consume("sms-token");
    }

    @Test
    void 대기표가_없으면_가입할_수_없다() {
        assertError(() -> service.register(가입("no-such-token", "길동이", "010-1234-5678")), UserErrorCode.SOCIAL_SIGNUP_EXPIRED);

        assertThat(users.existsByNickname("길동이")).isFalse();
        then(smsVerificationService).should(never()).checkVerified(anyString(), anyString());
    }

    @Test
    void 휴대폰_인증을_하지_않으면_가입할_수_없다() {
        String signupToken = 대기표();
        willThrow(new UserException(UserErrorCode.SMS_NOT_VERIFIED))
                .given(smsVerificationService).checkVerified(eq("sms-token"), anyString());

        assertError(() -> service.register(가입(signupToken, "길동이", "010-1234-5678")), UserErrorCode.SMS_NOT_VERIFIED);

        assertThat(socialUsers.exists(SocialType.GOOGLE, "google-sub-1")).isFalse();
        assertThat(signupTickets.store).as("대기표는 남아 있어 인증만 다시 하면 된다").containsKey(signupToken);
    }

    @Test
    void 만_14세_미만은_가입할_수_없다() {
        SocialRegisterCommand command = new SocialRegisterCommand(대기표(), "sms-token", "010-1234-5678", "길동이", "홍길동",
                Instant.now().minus(Duration.ofDays(365 * 10)), Gender.MALE);

        assertError(() -> service.register(command), UserErrorCode.UNDER_MINIMUM_AGE);
    }

    @Test
    void 같은_대기표로_두_번_가입할_수_없다() {
        String signupToken = 대기표();
        service.register(가입(signupToken, "길동이", "010-1234-5678"));

        assertError(() -> service.register(가입(signupToken, "다른닉", "010-9999-8888")), UserErrorCode.SOCIAL_SIGNUP_EXPIRED);
    }

    @Test
    void 대기표를_두_장_받아도_같은_구글_계정은_한_번만_가입된다() {
        String first = 대기표();
        String second = 대기표();
        service.register(가입(first, "길동이", "010-1234-5678"));

        assertError(() -> service.register(가입(second, "다른닉", "010-9999-8888")), UserErrorCode.SOCIAL_ALREADY_REGISTERED);
        assertThat(users.existsByNickname("다른닉")).isFalse();
    }

    @Test
    void 이미_가입된_전화번호면_409다() {
        User local = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL, "기존회원"));
        userProfiles.save(UserProfile.of(local.getId(), "김철수", Instant.parse("1990-01-01T00:00:00Z"), "010-1234-5678", Gender.MALE, "kim@test.com"));

        assertError(() -> service.register(가입(대기표(), "길동이", "010-1234-5678")), UserErrorCode.PHONE_NUMBER_DUPLICATED);

        assertThat(socialUsers.exists(SocialType.GOOGLE, "google-sub-1")).isFalse();
    }

    @Test
    void 이미_쓰는_닉네임이면_409이고_대기표는_남아_다시_시도할_수_있다() {
        users.save(User.from(UUID.randomUUID(), LoginType.LOCAL, "길동이"));
        String signupToken = 대기표();

        assertError(() -> service.register(가입(signupToken, " 길동이 ", "010-1234-5678")), UserErrorCode.NICKNAME_DUPLICATED);
        then(smsVerificationService).should(never()).consume(anyString());

        service.register(가입(signupToken, "새닉네임", "010-1234-5678"));

        assertThat(socialUsers.exists(SocialType.GOOGLE, "google-sub-1")).isTrue();
    }

    private static void assertError(Runnable call, UserErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    // ───────────────────────── 페이크 ─────────────────────────

    static class FakeSignupTickets implements SocialSignupTickets {
        final Map<String, SocialProfile> store = new HashMap<>();
        final Map<String, Duration> ttls = new HashMap<>();

        @Override
        public void save(String signupToken, SocialProfile profile, Duration ttl) {
            store.put(signupToken, profile);
            ttls.put(signupToken, ttl);
        }

        @Override
        public Optional<SocialProfile> find(String signupToken) { return Optional.ofNullable(store.get(signupToken)); }

        @Override
        public void delete(String signupToken) { store.remove(signupToken); }
    }

    static class FakeUserProfiles implements UserProfiles {
        private final Map<Long, UserProfile> byUserId = new HashMap<>();

        @Override
        public void save(UserProfile p) { byUserId.put(p.getUserId(), p); }

        @Override
        public Optional<UserProfile> findByUserId(Long userId) { return Optional.ofNullable(byUserId.get(userId)); }

        @Override
        public List<UserProfile> findAllByUserIds(List<Long> userIds) {
            return userIds.stream().map(byUserId::get).filter(Objects::nonNull).toList();
        }

        @Override
        public Optional<UserProfile> findByPhoneNumber(String phone) {
            return byUserId.values().stream().filter(p -> p.getPhoneNumber().equals(phone)).findFirst();
        }

        @Override
        public boolean existsByPhoneNumber(String phone) { return findByPhoneNumber(phone).isPresent(); }

        @Override
        public void deleteByUserId(Long userId) { byUserId.remove(userId); }
    }
}
