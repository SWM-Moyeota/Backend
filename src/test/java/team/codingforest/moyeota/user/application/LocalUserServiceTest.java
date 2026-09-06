package team.codingforest.moyeota.user.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.user.application.dto.UserRegisterCommand;
import team.codingforest.moyeota.user.application.dto.UserResponse;
import team.codingforest.moyeota.user.domain.*;
import team.codingforest.moyeota.user.domain.enums.Gender;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class LocalUserServiceTest {
    private FakeUsers users;
    private FakeLocalUsers localUsers;
    private FakeUserProfiles userProfiles;
    private LocalUserService service;

    @BeforeEach
    void setUp() {
        users = new FakeUsers();
        localUsers = new FakeLocalUsers();
        userProfiles = new FakeUserProfiles();
        service = new LocalUserService(users, localUsers, userProfiles,
                new PasswordHasher() {
                    public String hash(String p) { return "hashed:" + p; }
                    public boolean matches(String raw, String hashed) { return hashed.equals("hashed:" + raw); }
                },
                UUID::randomUUID);
    }

    private UserRegisterCommand 가입(String loginId, String nickname, String phone) {
        return new UserRegisterCommand(loginId, "Passw0rd!", nickname, "홍길동",
                Instant.parse("2000-01-01T00:00:00Z"), phone, Gender.MALE, loginId + "@test.com");
    }

    @Test
    void 가입하면_users에_닉네임이_저장되고_응답에도_실린다() {
        UserResponse response = service.register(가입("hong", "길동이", "010-1234-5678"));

        assertThat(response.name()).isEqualTo("길동이");
        assertThat(users.existsByNickname("길동이")).isTrue();
    }

    @Test
    void 닉네임_앞뒤_공백은_정규화되어_저장된다() {
        service.register(가입("hong", " 길동이 ", "010-1234-5678"));

        assertThat(users.existsByNickname("길동이")).isTrue();
    }

    @Test
    void 이미_쓰는_닉네임이면_409다() {
        service.register(가입("hong", "길동이", "010-1234-5678"));

        assertThatThrownBy(() -> service.register(가입("kim", "길동이", "010-9999-8888")))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.NICKNAME_DUPLICATED);
        assertThat(localUsers.existsByLoginId("kim")).isFalse();   // 어느 테이블에도 남지 않는다
    }

    @Test
    void 닉네임_중복_확인은_공백을_무시하고_비교한다() {
        service.register(가입("hong", "길동이", "010-1234-5678"));

        assertThat(service.existsByNickname(" 길동이 ")).isTrue();
        assertThat(service.existsByNickname("다른닉")).isFalse();
    }

    @Test
    void 형식이_틀린_닉네임은_가입_자체가_거부된다() {
        assertThatThrownBy(() -> service.register(가입("hong", "!", "010-1234-5678")))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_NICKNAME);
    }

    // ───────────────────────── 페이크 ─────────────────────────

    static class FakeLocalUsers implements LocalUsers {
        private final Map<String, LocalUser> byLoginId = new HashMap<>();

        @Override
        public void register(Long userId, String loginId, String password) {
            byLoginId.put(loginId, LocalUser.from(userId, loginId, password, Instant.now(), Instant.now()));
        }

        @Override
        public LocalUser findByLoginId(String loginId) {
            LocalUser u = byLoginId.get(loginId);
            if(u == null) throw new UserException(UserErrorCode.LOGIN_FAILED);
            return u;
        }

        @Override
        public boolean existsByLoginId(String loginId) { return byLoginId.containsKey(loginId); }
    }

    static class FakeUserProfiles implements UserProfiles {
        private final Map<Long, UserProfile> byUserId = new HashMap<>();

        @Override
        public void save(UserProfile p) { byUserId.put(p.getUserId(), p); }

        @Override
        public Optional<UserProfile> findByUserId(Long userId) { return Optional.ofNullable(byUserId.get(userId)); }

        @Override
        public boolean existsByPhoneNumber(String phone) {
            return byUserId.values().stream().anyMatch(p -> p.getPhoneNumber().equals(phone));
        }
    }
}
