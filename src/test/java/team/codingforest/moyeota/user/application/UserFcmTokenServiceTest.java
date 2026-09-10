package team.codingforest.moyeota.user.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.user.domain.User;
import team.codingforest.moyeota.user.domain.enums.LoginType;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class UserFcmTokenServiceTest {
    private FakeUsers users;
    private UserFcmTokenService service;
    private Long 승객;

    @BeforeEach
    void setUp() {
        users = new FakeUsers();
        service = new UserFcmTokenService(users);
        승객 = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL)).getId();
    }

    @Test
    void 토큰을_등록하면_저장_후에도_유지된다() {
        service.register(승객, "token-abc");

        // 저장→복원 왕복에서 토큰이 유실되는 버그를 잡는 테스트
        assertThat(users.findById(승객).getFcmToken()).isEqualTo("token-abc");
    }

    @Test
    void 토큰을_갱신하면_마지막_토큰만_남는다() {
        service.register(승객, "old-token");

        service.register(승객, "new-token");

        assertThat(users.findFcmTokens(List.of(승객))).containsExactly(entry(승객, "new-token"));
    }

    @Test
    void 빈_토큰은_등록할_수_없다() {
        assertThatThrownBy(() -> service.register(승객, "  "))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.EMPTY_FCM_TOKEN);
    }

    @Test
    void 없는_유저의_토큰은_등록할_수_없다() {
        assertThatThrownBy(() -> service.register(999L, "token-abc"))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }

    @Test
    void 토큰을_제거하면_알림_대상에서_빠진다() {
        service.register(승객, "token-abc");

        service.remove(승객);

        assertThat(users.findById(승객).hasFcmToken()).isFalse();
        assertThat(users.findFcmTokens(List.of(승객))).isEmpty();
    }

    @Test
    void 토큰이_없는_상태에서_제거해도_예외가_없다() {
        // 로그아웃 직전 프론트가 항상 호출하므로 멱등해야 한다
        assertThatCode(() -> service.remove(승객)).doesNotThrowAnyException();
    }
}
