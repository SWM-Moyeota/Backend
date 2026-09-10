package team.codingforest.moyeota.user.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.user.domain.User;
import team.codingforest.moyeota.user.domain.enums.LoginType;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserProfileServiceTest {
    private FakeUsers users;
    private UserProfileService service;
    private Long 나;

    @BeforeEach
    void setUp() {
        users = new FakeUsers();
        service = new UserProfileService(users);
        나 = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL, "원래닉")).getId();
        users.save(User.from(UUID.randomUUID(), LoginType.LOCAL, "남의닉"));
    }

    @Test
    void 닉네임만_바꾸면_이미지는_유지된다() {
        service.update(나, null, "https://img/1.png");

        service.update(나, "새닉", null);

        User user = users.findById(나);
        assertThat(user.getNickname()).isEqualTo("새닉");
        assertThat(user.getImageUrl()).isEqualTo("https://img/1.png");
    }

    @Test
    void 이미지만_바꾸면_닉네임은_유지된다() {
        service.update(나, null, "https://img/2.png");

        User user = users.findById(나);
        assertThat(user.getNickname()).isEqualTo("원래닉");
        assertThat(user.getImageUrl()).isEqualTo("https://img/2.png");
    }

    @Test
    void 남이_쓰는_닉네임으로는_바꿀_수_없다() {
        assertThatThrownBy(() -> service.update(나, "남의닉", null))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.NICKNAME_DUPLICATED);
    }

    @Test
    void 자기_닉네임을_그대로_보내면_통과한다() {
        // 프로필 화면이 전체 폼을 다시 보내는 경우 - 여기서 409 가 나면 이미지만 바꾸는 것도 막힌다
        assertThatCode(() -> service.update(나, "원래닉", "https://img/3.png")).doesNotThrowAnyException();
        assertThat(users.findById(나).getImageUrl()).isEqualTo("https://img/3.png");
    }

    @Test
    void 형식이_틀린_닉네임은_저장_전에_거부된다() {
        assertThatThrownBy(() -> service.update(나, "!", null))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_NICKNAME);
        assertThat(users.findById(나).getNickname()).isEqualTo("원래닉");
    }

    @Test
    void 소셜_가입자는_닉네임이_비어_있어도_처음_설정할_수_있다() {
        Long 소셜 = users.save(User.from(UUID.randomUUID(), LoginType.SOCIAL)).getId();

        service.update(소셜, "첫닉", null);

        assertThat(users.findById(소셜).getNickname()).isEqualTo("첫닉");
    }
}
