package team.codingforest.moyeota.user.users.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
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

@ExtendWith(MockitoExtension.class)
class UserFcmTokenServiceUnitTest {
    private static final Long USER_ID = 1L;
    private static final UUID PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String OLD_TOKEN = "old-token";
    private static final String NEW_TOKEN = "new-token";

    @Mock
    private Users users;
    @InjectMocks
    private UserFcmTokenService service;

    private static User userWithToken(String token) {
        return User.restore(USER_ID, PUBLIC_ID, "모여타", null, LoginType.LOCAL, null, token, Instant.EPOCH, null);
    }

    @DisplayName("register - 토큰이 없던 사용자에게 저장")
    @Test
    void register() {

        /*
         * settings
         */
        final User user = userWithToken(null);
        doReturn(user).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        service.register(USER_ID, NEW_TOKEN);

        /*
         * verify
         */
        assertThat(user.getFcmToken()).isEqualTo(NEW_TOKEN);
        verify(users).save(eq(user));
    }

    @DisplayName("register - 이미 있던 토큰은 새 토큰으로 교체")
    @Test
    void register_replace() {

        /*
         * settings
         */
        final User user = userWithToken(OLD_TOKEN);
        doReturn(user).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        service.register(USER_ID, NEW_TOKEN);

        /*
         * verify
         */
        assertThat(user.getFcmToken()).isEqualTo(NEW_TOKEN);
        verify(users).save(eq(user));
    }

    @DisplayName("register - 토큰이 null 이거나 비어 있으면 기존 토큰을 유지하고 실패")
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void register_emptyToken(String token) {

        /*
         * settings
         */
        final User user = userWithToken(OLD_TOKEN);
        doReturn(user).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.register(USER_ID, token));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(UserException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.EMPTY_FCM_TOKEN);
        assertThat(user.getFcmToken()).isEqualTo(OLD_TOKEN);
        verify(users, never()).save(any());
    }

    @DisplayName("register - 사용자가 없으면 저장하지 않고 실패")
    @Test
    void register_userNotFound() {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.USER_NOT_FOUND)).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.register(USER_ID, NEW_TOKEN));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(UserException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
        verify(users, never()).save(any());
    }

    @DisplayName("remove - 토큰을 비우고 저장")
    @Test
    void remove() {

        /*
         * settings
         */
        final User user = userWithToken(OLD_TOKEN);
        doReturn(user).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        service.remove(USER_ID);

        /*
         * verify
         */
        assertThat(user.getFcmToken()).isNull();
        verify(users).save(eq(user));
    }

    @DisplayName("remove - 이미 토큰이 없어도 실패하지 않는다(멱등)")
    @Test
    void remove_noToken() {

        /*
         * settings
         */
        final User user = userWithToken(null);
        doReturn(user).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        service.remove(USER_ID);

        /*
         * verify
         */
        assertThat(user.getFcmToken()).isNull();
        verify(users).save(eq(user));
    }

    @DisplayName("remove - 사용자가 없으면 저장하지 않고 실패")
    @Test
    void remove_userNotFound() {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.USER_NOT_FOUND)).when(users).findById(eq(USER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.remove(USER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(UserException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
        verify(users, never()).save(any());
    }
}
