package team.codingforest.moyeota.user.users.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.enums.LoginType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest {
    private static final String 이미지1 = "profile/presets/01.png";
    private static final String 이미지2 = "profile/presets/02.png";
    private static final String 이미지3 = "profile/presets/03.png";

    private FakeUsers users;
    private UserService service;
    private Long 나;

    @BeforeEach
    void setUp() {
        users = new FakeUsers();
        ProfileImages profileImages = new ProfileImages(new ProfileImageProperties(
                "https://cdn.example.com/", "profile/presets/default.png", List.of(이미지1, 이미지2, 이미지3)));
        service = new UserService(users, profileImages);
        나 = users.save(User.from(UUID.randomUUID(), LoginType.LOCAL, "원래닉")).getId();
        users.save(User.from(UUID.randomUUID(), LoginType.LOCAL, "남의닉"));
    }

    @Test
    void 닉네임만_바꾸면_이미지는_유지된다() {
        service.update(나, null, 이미지1);

        service.update(나, "새닉", null);

        User user = users.findById(나);
        assertThat(user.getNickname()).isEqualTo("새닉");
        assertThat(user.getImageUrl()).isEqualTo(이미지1);
    }

    @Test
    void 이미지만_바꾸면_닉네임은_유지된다() {
        service.update(나, null, 이미지2);

        User user = users.findById(나);
        assertThat(user.getNickname()).isEqualTo("원래닉");
        assertThat(user.getImageUrl()).isEqualTo(이미지2);
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
        assertThatCode(() -> service.update(나, "원래닉", 이미지3)).doesNotThrowAnyException();
        assertThat(users.findById(나).getImageUrl()).isEqualTo(이미지3);
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

    @Test
    void 제공하지_않는_이미지_키는_거부하고_아무것도_바꾸지_않는다() {
        service.update(나, null, 이미지1);

        assertThatThrownBy(() -> service.update(나, "새닉", "https://evil.example.com/x.png"))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_PROFILE_IMAGE);

        User user = users.findById(나);
        assertThat(user.getNickname()).isEqualTo("원래닉");
        assertThat(user.getImageUrl()).isEqualTo(이미지1);
    }

    @Test
    void 기본_이미지_키는_직접_고를_수_없다() {
        // 기본으로 돌아가려면 DELETE 로 비워야 한다. 키로 저장하면 기본 이미지가 바뀔 때 따라가지 못함
        assertThatThrownBy(() -> service.update(나, null, "profile/presets/default.png"))
                .isInstanceOf(UserException.class)
                .extracting("errorCode")
                .isEqualTo(UserErrorCode.INVALID_PROFILE_IMAGE);
    }

    @Test
    void 이미지를_되돌리면_저장값이_비고_닉네임은_유지된다() {
        service.update(나, null, 이미지1);

        service.resetImage(나);

        User user = users.findById(나);
        assertThat(user.getImageUrl()).isNull();
        assertThat(user.getNickname()).isEqualTo("원래닉");
    }

    @Test
    void 고른_적_없는_사용자가_되돌려도_성공한다() {
        assertThatCode(() -> service.resetImage(나)).doesNotThrowAnyException();
        assertThat(users.findById(나).getImageUrl()).isNull();
    }

    @Test
    void 되돌린_뒤_닉네임만_바꿔도_기본_이미지가_유지된다() {
        service.update(나, null, 이미지1);
        service.resetImage(나);

        service.update(나, "새닉", null);

        assertThat(users.findById(나).getImageUrl()).isNull();
    }
}
