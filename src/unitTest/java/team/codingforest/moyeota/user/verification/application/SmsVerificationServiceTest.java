package team.codingforest.moyeota.user.verification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import team.codingforest.moyeota.user.common.crypto.FieldHasher;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.profile.domain.UserProfile;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;
import team.codingforest.moyeota.user.verification.domain.SmsSender;
import team.codingforest.moyeota.user.verification.domain.SmsVerifications;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmsVerificationServiceTest {
    private static final String PHONE = "010-1234-5678";
    private static final String CODE = "123456";

    private FakeSmsVerifications store;
    private FakeSmsSender sender;
    private FakeUserProfiles userProfiles;
    private FieldHasher fieldHasher;
    private SmsVerificationService service;

    @BeforeEach
    void setUp() {
        store = new FakeSmsVerifications();
        sender = new FakeSmsSender();
        userProfiles = new FakeUserProfiles();
        fieldHasher = new FieldHasher("test-hash-key");
        service = serviceWith("log", CODE);   // 대부분은 코드를 알아야 해서 고정 코드로 둠
    }

    private SmsVerificationService serviceWith(String provider, String fixedCode) {
        return new SmsVerificationService(store, sender, userProfiles, fieldHasher, provider, fixedCode);
    }

    private static void assertError(Runnable call, UserErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    // ───────────────────────── 발송 ─────────────────────────

    @Test
    void 고정_코드가_없으면_랜덤_6자리를_하이픈_없는_번호로_보낸다() {
        serviceWith("log", "").send(PHONE);

        assertThat(sender.sent).hasSize(1);
        assertThat(sender.sent.get(0).to()).isEqualTo("01012345678");
        assertThat(sender.sent.get(0).text()).containsPattern("\\[\\d{6}]");
    }

    @Test
    void 이미_가입된_번호면_문자를_보내지_않는다() {
        userProfiles.registered.add("01012345678");

        assertError(() -> service.send(PHONE), UserErrorCode.PHONE_NUMBER_DUPLICATED);
        assertThat(sender.sent).isEmpty();
    }

    @Test
    void 일분_안에_다시_요청하면_거절한다() {
        service.send(PHONE);

        assertError(() -> service.send(PHONE), UserErrorCode.SMS_COOLDOWN);
        assertThat(sender.sent).hasSize(1);
    }

    @Test
    void 하루_다섯_번을_넘으면_거절한다() {
        for (int i = 0; i < 5; i++) {
            service.send(PHONE);
            store.cooldowns.clear();   // 1분이 지났다고 침
        }

        assertError(() -> service.send(PHONE), UserErrorCode.SMS_DAILY_LIMIT_EXCEEDED);
        assertThat(sender.sent).hasSize(5);
    }

    @Test
    void 발송이_실패하면_기다리지_않고_다시_요청할_수_있다() {
        sender.failNext = true;
        assertError(() -> service.send(PHONE), UserErrorCode.SMS_SEND_FAILED);

        service.send(PHONE);

        assertThat(sender.sent).hasSize(1);
    }

    @Test
    void Redis_가_죽으면_503_으로_바꾼다() {
        store.down = true;

        assertError(() -> service.send(PHONE), UserErrorCode.SMS_UNAVAILABLE);
        assertError(() -> service.verify(PHONE, CODE), UserErrorCode.SMS_UNAVAILABLE);
    }

    // ───────────────────────── 확인 ─────────────────────────

    @Test
    void 맞는_코드면_토큰을_주고_코드는_한_번만_쓸_수_있다() {
        service.send(PHONE);

        String token = service.verify(PHONE, CODE);

        assertThat(token).isNotBlank();
        assertError(() -> service.verify(PHONE, CODE), UserErrorCode.SMS_CODE_EXPIRED);
    }

    @Test
    void 요청한_적이_없으면_만료로_본다() {
        assertError(() -> service.verify(PHONE, CODE), UserErrorCode.SMS_CODE_EXPIRED);
    }

    @Test
    void 네_번까지는_불일치_다섯_번째_틀리면_코드가_무효가_된다() {
        service.send(PHONE);
        for (int i = 0; i < 4; i++) {
            assertError(() -> service.verify(PHONE, "000000"), UserErrorCode.SMS_CODE_MISMATCH);
        }

        assertError(() -> service.verify(PHONE, "000000"), UserErrorCode.SMS_TOO_MANY_ATTEMPTS);
        assertError(() -> service.verify(PHONE, CODE), UserErrorCode.SMS_CODE_EXPIRED);   // 맞는 코드도 이제 안 됨
    }

    @Test
    void 다시_받으면_틀린_횟수가_초기화된다() {
        service.send(PHONE);
        for (int i = 0; i < 4; i++) {
            assertError(() -> service.verify(PHONE, "000000"), UserErrorCode.SMS_CODE_MISMATCH);
        }
        store.cooldowns.clear();
        service.send(PHONE);

        assertError(() -> service.verify(PHONE, "000000"), UserErrorCode.SMS_CODE_MISMATCH);   // 5번째가 아니라 1번째
    }

    // ───────────────────────── 가입 연결 ─────────────────────────

    @Test
    void 인증한_번호의_토큰이면_가입_확인을_통과한다() {
        service.send(PHONE);
        String token = service.verify(PHONE, CODE);

        service.checkVerified(token, "01012345678");   // 하이픈 유무가 달라도 같은 번호
    }

    @Test
    void 다른_번호로_받은_토큰은_거절한다() {
        service.send(PHONE);
        String token = service.verify(PHONE, CODE);

        assertError(() -> service.checkVerified(token, "010-9999-8888"), UserErrorCode.SMS_NOT_VERIFIED);
    }

    @Test
    void 소비한_토큰은_다시_쓸_수_없다() {
        service.send(PHONE);
        String token = service.verify(PHONE, CODE);

        service.consume(token);

        assertError(() -> service.checkVerified(token, PHONE), UserErrorCode.SMS_NOT_VERIFIED);
    }

    // ───────────────────────── 고정 코드 안전장치 ─────────────────────────

    @Test
    void 실제_문자를_보내는_환경에서_고정_코드를_켜면_기동이_막힌다() {
        assertThatThrownBy(() -> serviceWith("solapi", "000000"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 고정_코드가_6자리_숫자가_아니면_기동이_막힌다() {
        assertThatThrownBy(() -> serviceWith("log", "1234"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ───────────────────────── 페이크 ─────────────────────────

    // TTL 은 흉내 내지 않음 - 시간이 지난 상황은 테스트에서 직접 지워서 만듦
    static class FakeSmsVerifications implements SmsVerifications {
        final Set<String> cooldowns = new HashSet<>();
        final Map<String, Long> daily = new HashMap<>();
        final Map<String, String> codes = new HashMap<>();
        final Map<String, Long> attempts = new HashMap<>();
        final Map<String, String> verified = new HashMap<>();
        boolean down;

        @Override
        public boolean tryStartCooldown(String phoneHash, Duration cooldown) { check(); return cooldowns.add(phoneHash); }

        @Override
        public void releaseCooldown(String phoneHash) { check(); cooldowns.remove(phoneHash); }

        @Override
        public long increaseDailyCount(String phoneHash, Duration window) { check(); return daily.merge(phoneHash, 1L, Long::sum); }

        @Override
        public void saveCode(String phoneHash, String code, Duration ttl) { check(); attempts.remove(phoneHash); codes.put(phoneHash, code); }

        @Override
        public Optional<String> findCode(String phoneHash) { check(); return Optional.ofNullable(codes.get(phoneHash)); }

        @Override
        public long increaseAttempts(String phoneHash, Duration ttl) { check(); return attempts.merge(phoneHash, 1L, Long::sum); }

        @Override
        public void deleteCode(String phoneHash) { check(); codes.remove(phoneHash); attempts.remove(phoneHash); }

        @Override
        public void saveVerified(String token, String phoneHash, Duration ttl) { check(); verified.put(token, phoneHash); }

        @Override
        public Optional<String> findVerifiedPhone(String token) { check(); return Optional.ofNullable(verified.get(token)); }

        @Override
        public void deleteVerified(String token) { check(); verified.remove(token); }

        private void check() {
            if (down) throw new RedisConnectionFailureException("테스트용 장애");
        }
    }

    record Sent(String to, String text) {
    }

    static class FakeSmsSender implements SmsSender {
        final List<Sent> sent = new ArrayList<>();
        boolean failNext;

        @Override
        public void send(String phoneNumber, String text) {
            if (failNext) {
                failNext = false;
                throw new UserException(UserErrorCode.SMS_SEND_FAILED);
            }
            sent.add(new Sent(phoneNumber, text));
        }
    }

    static class FakeUserProfiles implements UserProfiles {
        final Set<String> registered = new HashSet<>();

        @Override
        public void save(UserProfile userProfile) { }

        @Override
        public Optional<UserProfile> findByUserId(Long userId) { return Optional.empty(); }

        @Override
        public boolean existsByPhoneNumber(String normalizedPhoneNumber) { return registered.contains(normalizedPhoneNumber); }
    }
}
