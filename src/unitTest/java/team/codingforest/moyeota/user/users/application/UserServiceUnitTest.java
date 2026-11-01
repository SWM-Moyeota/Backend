package team.codingforest.moyeota.user.users.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.Users;
import team.codingforest.moyeota.user.users.domain.enums.LoginType;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserServiceUnitTest {
    private static final Long USER_ID = 1L;
    private static final UUID PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String OLD_NICKNAME = "기존닉";
    private static final String NEW_NICKNAME = "새닉네임";
    private static final String OLD_IMAGE_KEY = "profile/presets/01.png";
    private static final String NEW_IMAGE_KEY = "profile/presets/03.png";

    @Mock
    private Users users;
    @Mock
    private ProfileImages profileImages;
    @InjectMocks
    private UserService service;

    private static User user(String nickname, String imageKey) {
        return User.restore(USER_ID, PUBLIC_ID, nickname, imageKey, LoginType.LOCAL, null, null, Instant.EPOCH, null);
    }

    @DisplayName("update - 닉네임과 이미지를 모두 바꾸고 저장")
    @Test
    void update_nicknameAndImage() {

        /*
         * settings
         */
        final User user = user(OLD_NICKNAME, OLD_IMAGE_KEY);
        doReturn(true).when(profileImages).isPreset(eq(NEW_IMAGE_KEY));
        doReturn(user).when(users).findById(eq(USER_ID));
        doReturn(false).when(users).existsByNickname(eq(NEW_NICKNAME));

        /*
         * test
         */
        service.update(USER_ID, NEW_NICKNAME, NEW_IMAGE_KEY);

        /*
         * verify
         */
        assertThat(user.getNickname()).isEqualTo(NEW_NICKNAME);
        assertThat(user.getImageUrl()).isEqualTo(NEW_IMAGE_KEY);
        verify(users).save(eq(user));
    }

    @DisplayName("update - 닉네임만 오면 이미지는 유지하고 이미지 검사를 하지 않는다")
    @Test
    void update_nicknameOnly() {

        /*
         * settings
         */
        final User user = user(OLD_NICKNAME, OLD_IMAGE_KEY);
        doReturn(user).when(users).findById(eq(USER_ID));
        doReturn(false).when(users).existsByNickname(eq(NEW_NICKNAME));

        /*
         * test
         */
        service.update(USER_ID, NEW_NICKNAME, null);

        /*
         * verify
         */
        assertThat(user.getNickname()).isEqualTo(NEW_NICKNAME);
        assertThat(user.getImageUrl()).isEqualTo(OLD_IMAGE_KEY);
        verifyNoInteractions(profileImages);
        verify(users).save(eq(user));
    }

    @DisplayName("update - 이미지만 오면 닉네임은 유지하고 중복 검사를 하지 않는다")
    @Test
    void update_imageOnly() {

        /*
         * settings
         */
        final User user = user(OLD_NICKNAME, OLD_IMAGE_KEY);
        doReturn(true).when(profileImages).isPreset(eq(NEW_IMAGE_KEY));
        doReturn(user).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        service.update(USER_ID, null, NEW_IMAGE_KEY);

        /*
         * verify
         */
        assertThat(user.getNickname()).isEqualTo(OLD_NICKNAME);
        assertThat(user.getImageUrl()).isEqualTo(NEW_IMAGE_KEY);
        verify(users, never()).existsByNickname(any());
        verify(users).save(eq(user));
    }

    @DisplayName("update - 둘 다 null 이면 닉네임과 이미지가 그대로이고 검사를 하지 않는다")
    @Test
    void update_nothing() {

        /*
         * settings
         */
        final User user = user(OLD_NICKNAME, OLD_IMAGE_KEY);
        doReturn(user).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        service.update(USER_ID, null, null);

        /*
         * verify
         */
        assertThat(user.getNickname()).isEqualTo(OLD_NICKNAME);
        assertThat(user.getImageUrl()).isEqualTo(OLD_IMAGE_KEY);
        verifyNoInteractions(profileImages);
        verify(users, never()).existsByNickname(any());
    }

    @DisplayName("update - 제공 목록에 없는 이미지 키는 사용자 조회 전에 실패")
    @ParameterizedTest
    @ValueSource(strings = {"", "https://evil.example.com/a.png", "profile/presets/99.png"})
    void update_invalidImageKey(String imageKey) {

        /*
         * settings
         */
        doReturn(false).when(profileImages).isPreset(eq(imageKey));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.update(USER_ID, NEW_NICKNAME, imageKey));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(UserException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.INVALID_PROFILE_IMAGE);
        verifyNoInteractions(users);
    }

    @DisplayName("update - 사용자가 없으면 중복 검사와 저장 없이 실패")
    @Test
    void update_userNotFound() {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.USER_NOT_FOUND)).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.update(USER_ID, NEW_NICKNAME, null));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(UserException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
        verify(users, never()).existsByNickname(any());
        verify(users, never()).save(any());
    }

    @DisplayName("update - 닉네임 형식이 틀리면 중복 검사와 저장 없이 실패")
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "가", "abcdefghijk", "닉네임!", "닉 네임"})
    void update_invalidNickname(String nickname) {

        /*
         * settings
         */
        final User user = user(OLD_NICKNAME, OLD_IMAGE_KEY);
        doReturn(user).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.update(USER_ID, nickname, null));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(UserException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.INVALID_NICKNAME);
        assertThat(user.getNickname()).isEqualTo(OLD_NICKNAME);
        verify(users, never()).existsByNickname(any());
        verify(users, never()).save(any());
    }

    @DisplayName("update - 2자와 10자 닉네임은 허용(경계값)")
    @ParameterizedTest
    @ValueSource(strings = {"가나", "가나다라마바사아자차"})
    void update_nicknameLengthBoundary(String nickname) {

        /*
         * settings
         */
        final User user = user(OLD_NICKNAME, OLD_IMAGE_KEY);
        doReturn(user).when(users).findById(eq(USER_ID));
        doReturn(false).when(users).existsByNickname(eq(nickname));

        /*
         * test
         */
        service.update(USER_ID, nickname, null);

        /*
         * verify
         */
        assertThat(user.getNickname()).isEqualTo(nickname);
        verify(users).save(eq(user));
    }

    @DisplayName("update - 앞뒤 공백을 지운 닉네임으로 중복을 검사하고 저장")
    @Test
    void update_nicknameStripped() {

        /*
         * settings
         */
        final User user = user(OLD_NICKNAME, OLD_IMAGE_KEY);
        doReturn(user).when(users).findById(eq(USER_ID));
        doReturn(false).when(users).existsByNickname(eq(NEW_NICKNAME));

        /*
         * test
         */
        service.update(USER_ID, "  " + NEW_NICKNAME + "  ", null);

        /*
         * verify
         */
        assertThat(user.getNickname()).isEqualTo(NEW_NICKNAME);
        verify(users).existsByNickname(eq(NEW_NICKNAME));
        verify(users).save(eq(user));
    }

    @DisplayName("update - 자기 닉네임을 그대로 보내면 중복 검사를 하지 않는다")
    @Test
    void update_sameNickname() {

        /*
         * settings
         */
        final User user = user(OLD_NICKNAME, OLD_IMAGE_KEY);
        doReturn(user).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        service.update(USER_ID, OLD_NICKNAME, null);

        /*
         * verify
         */
        assertThat(user.getNickname()).isEqualTo(OLD_NICKNAME);
        verify(users, never()).existsByNickname(any());
        verify(users).save(eq(user));
    }

    @DisplayName("update - 공백만 다른 자기 닉네임도 중복 검사를 하지 않는다")
    @Test
    void update_sameNicknameWithSpaces() {

        /*
         * settings
         */
        final User user = user(OLD_NICKNAME, OLD_IMAGE_KEY);
        doReturn(user).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        service.update(USER_ID, " " + OLD_NICKNAME + " ", null);

        /*
         * verify
         */
        assertThat(user.getNickname()).isEqualTo(OLD_NICKNAME);
        verify(users, never()).existsByNickname(any());
        verify(users).save(eq(user));
    }

    @DisplayName("update - 닉네임을 정한 적 없는 사용자도 중복 검사 후 저장")
    @Test
    void update_firstNickname() {

        /*
         * settings
         */
        final User user = user(null, null);
        doReturn(user).when(users).findById(eq(USER_ID));
        doReturn(false).when(users).existsByNickname(eq(NEW_NICKNAME));

        /*
         * test
         */
        service.update(USER_ID, NEW_NICKNAME, null);

        /*
         * verify
         */
        assertThat(user.getNickname()).isEqualTo(NEW_NICKNAME);
        verify(users).existsByNickname(eq(NEW_NICKNAME));
        verify(users).save(eq(user));
    }

    @DisplayName("update - 다른 사람이 쓰는 닉네임이면 이미지까지 바꾸지 않고 실패")
    @Test
    void update_nicknameDuplicated() {

        /*
         * settings
         */
        final User user = user(OLD_NICKNAME, OLD_IMAGE_KEY);
        doReturn(true).when(profileImages).isPreset(eq(NEW_IMAGE_KEY));
        doReturn(user).when(users).findById(eq(USER_ID));
        doReturn(true).when(users).existsByNickname(eq(NEW_NICKNAME));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.update(USER_ID, NEW_NICKNAME, NEW_IMAGE_KEY));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(UserException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.NICKNAME_DUPLICATED);
        assertThat(user.getNickname()).isEqualTo(OLD_NICKNAME);
        assertThat(user.getImageUrl()).isEqualTo(OLD_IMAGE_KEY);
        verify(users, never()).save(any());
    }

    @DisplayName("resetImage - 고른 이미지를 지우고 저장")
    @Test
    void resetImage() {

        /*
         * settings
         */
        final User user = user(OLD_NICKNAME, OLD_IMAGE_KEY);
        doReturn(user).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        service.resetImage(USER_ID);

        /*
         * verify
         */
        assertThat(user.getImageUrl()).isNull();
        assertThat(user.getNickname()).isEqualTo(OLD_NICKNAME);
        verify(users).save(eq(user));
    }

    @DisplayName("resetImage - 이미 기본 이미지여도 실패하지 않는다(멱등)")
    @Test
    void resetImage_alreadyDefault() {

        /*
         * settings
         */
        final User user = user(OLD_NICKNAME, null);
        doReturn(user).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        service.resetImage(USER_ID);

        /*
         * verify
         */
        assertThat(user.getImageUrl()).isNull();
        verify(users).save(eq(user));
    }

    @DisplayName("resetImage - 사용자가 없으면 저장하지 않고 실패")
    @Test
    void resetImage_userNotFound() {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.USER_NOT_FOUND)).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.resetImage(USER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(UserException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
        verify(users, never()).save(any());
    }
}
