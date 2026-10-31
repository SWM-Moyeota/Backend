package team.codingforest.moyeota.user.users.presentation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.users.application.ProfileImages;
import team.codingforest.moyeota.user.users.application.UserFcmTokenService;
import team.codingforest.moyeota.user.users.application.UserService;
import team.codingforest.moyeota.user.users.dto.ProfileImageResponse;

import java.util.List;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerUnitTest extends ControllerUnitTestSupport {
    private static final String PROFILE_URL = "/api/v1/users/me";
    private static final String PROFILE_IMAGES_URL = "/api/v1/users/profile-images";
    private static final String PROFILE_IMAGE_URL = "/api/v1/users/me/profile-image";
    private static final String FCM_TOKEN_URL = "/api/v1/users/me/fcm-token";
    private static final String NICKNAME = "새닉";
    private static final String IMAGE_KEY = "profile/presets/03.png";
    private static final String FCM_TOKEN = "fcm-token";
    private static final String PROFILE_REQUEST = """
            {
                "nickname": "새닉",
                "imageKey": "profile/presets/03.png"
            }
            """;
    private static final String FCM_TOKEN_REQUEST = """
            {
                "token": "fcm-token"
            }
            """;

    @MockitoBean
    private UserFcmTokenService fcmTokenService;
    @MockitoBean
    private UserService userService;
    @MockitoBean
    private ProfileImages profileImages;

    @DisplayName("PATCH /users/me - 성공하면 204 이고 토큰의 USER_ID 와 요청 값이 서비스로 간다")
    @Test
    void updateProfile() throws Exception {

        /*
         * test
         */
        mockMvc.perform(patch(PROFILE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(PROFILE_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(userService).update(eq(USER_ID), eq(NICKNAME), eq(IMAGE_KEY));
    }

    @DisplayName("PATCH /users/me - 닉네임만 보내면 imageKey 는 null 로 넘어간다")
    @Test
    void updateProfile_nicknameOnly() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "nickname": "새닉"
                }
                """;

        /*
         * test
         */
        mockMvc.perform(patch(PROFILE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(userService).update(eq(USER_ID), eq(NICKNAME), eq(null));
    }

    @DisplayName("PATCH /users/me - 이미지만 보내면 nickname 은 null 로 넘어간다")
    @Test
    void updateProfile_imageOnly() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "imageKey": "profile/presets/03.png"
                }
                """;

        /*
         * test
         */
        mockMvc.perform(patch(PROFILE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(userService).update(eq(USER_ID), eq(null), eq(IMAGE_KEY));
    }

    @DisplayName("PATCH /users/me - 둘 다 없어도 204")
    @Test
    void updateProfile_empty() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                }
                """;

        /*
         * test
         */
        mockMvc.perform(patch(PROFILE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(userService).update(eq(USER_ID), eq(null), eq(null));
    }

    @DisplayName("PATCH /users/me - 2자와 10자 닉네임은 검증을 통과(경계값)")
    @ParameterizedTest
    @ValueSource(strings = {"가나", "가나다라마바사아자차"})
    void updateProfile_nicknameLengthBoundary(String nickname) throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "nickname": "%s"
                }
                """.formatted(nickname);

        /*
         * test
         */
        mockMvc.perform(patch(PROFILE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(userService).update(eq(USER_ID), eq(nickname), eq(null));
    }

    @DisplayName("PATCH /users/me - 닉네임이 2자 미만이거나 10자를 넘으면 400")
    @ParameterizedTest
    @ValueSource(strings = {"", "가", "가나다라마바사아자차카"})
    void updateProfile_nicknameLengthInvalid(String nickname) throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "nickname": "%s"
                }
                """.formatted(nickname);

        /*
         * test
         */
        mockMvc.perform(patch(PROFILE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("nickname: 닉네임은 2~10자여야 합니다."));
        verifyNoInteractions(userService);
    }

    @DisplayName("PATCH /users/me - 토큰이 없으면 401")
    @Test
    void updateProfile_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(patch(PROFILE_URL).contentType(MediaType.APPLICATION_JSON).content(PROFILE_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(userService);
    }

    @DisplayName("PATCH /users/me - 본문이 없으면 400")
    @Test
    void updateProfile_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(patch(PROFILE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(userService);
    }

    @DisplayName("PATCH /users/me - 형식이 틀린 닉네임은 서비스의 INVALID_NICKNAME 이 400 으로 나간다")
    @Test
    void updateProfile_invalidNickname() throws Exception {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.INVALID_NICKNAME)).when(userService).update(eq(USER_ID), eq(NICKNAME), eq(IMAGE_KEY));

        /*
         * test
         */
        mockMvc.perform(patch(PROFILE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(PROFILE_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER107"))
                .andExpect(jsonPath("$.message").value(UserErrorCode.INVALID_NICKNAME.getMessage()));
    }

    @DisplayName("PATCH /users/me - 제공하지 않는 이미지면 400")
    @Test
    void updateProfile_invalidImage() throws Exception {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.INVALID_PROFILE_IMAGE)).when(userService).update(eq(USER_ID), eq(NICKNAME), eq(IMAGE_KEY));

        /*
         * test
         */
        mockMvc.perform(patch(PROFILE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(PROFILE_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER112"));
    }

    @DisplayName("PATCH /users/me - 닉네임이 중복이면 409")
    @Test
    void updateProfile_nicknameDuplicated() throws Exception {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.NICKNAME_DUPLICATED)).when(userService).update(eq(USER_ID), eq(NICKNAME), eq(IMAGE_KEY));

        /*
         * test
         */
        mockMvc.perform(patch(PROFILE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(PROFILE_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER108"));
    }

    @DisplayName("PATCH /users/me - 사용자가 없으면 404")
    @Test
    void updateProfile_userNotFound() throws Exception {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.USER_NOT_FOUND)).when(userService).update(eq(USER_ID), eq(NICKNAME), eq(IMAGE_KEY));

        /*
         * test
         */
        mockMvc.perform(patch(PROFILE_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(PROFILE_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER103"));
    }

    @DisplayName("GET /users/profile-images - 제공 이미지를 순서 그대로 반환")
    @Test
    void getProfileImages() throws Exception {

        /*
         * settings
         */
        doReturn(List.of(
                new ProfileImageResponse("profile/presets/02.png", "https://cdn.example.com/profile/presets/02.png"),
                new ProfileImageResponse("profile/presets/01.png", "https://cdn.example.com/profile/presets/01.png")
        )).when(profileImages).presets();

        /*
         * test
         */
        mockMvc.perform(get(PROFILE_IMAGES_URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].key").value("profile/presets/02.png"))
                .andExpect(jsonPath("$[0].url").value("https://cdn.example.com/profile/presets/02.png"))
                .andExpect(jsonPath("$[1].key").value("profile/presets/01.png"))
                .andExpect(jsonPath("$[1].url").value("https://cdn.example.com/profile/presets/01.png"));
        verify(profileImages).presets();
    }

    @DisplayName("GET /users/profile-images - 제공 이미지가 없으면 빈 배열")
    @Test
    void getProfileImages_empty() throws Exception {

        /*
         * settings
         */
        doReturn(List.of()).when(profileImages).presets();

        /*
         * test
         */
        mockMvc.perform(get(PROFILE_IMAGES_URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @DisplayName("GET /users/profile-images - 토큰이 없으면 401")
    @Test
    void getProfileImages_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(PROFILE_IMAGES_URL))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(profileImages);
    }

    @DisplayName("DELETE /users/me/profile-image - 성공하면 204 이고 토큰의 USER_ID 가 서비스로 간다")
    @Test
    void resetProfileImage() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete(PROFILE_IMAGE_URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(userService).resetImage(eq(USER_ID));
    }

    @DisplayName("DELETE /users/me/profile-image - 토큰이 없으면 401")
    @Test
    void resetProfileImage_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete(PROFILE_IMAGE_URL))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(userService);
    }

    @DisplayName("DELETE /users/me/profile-image - 사용자가 없으면 404")
    @Test
    void resetProfileImage_userNotFound() throws Exception {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.USER_NOT_FOUND)).when(userService).resetImage(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(delete(PROFILE_IMAGE_URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER103"));
    }

    @DisplayName("PUT /users/me/fcm-token - 성공하면 204 이고 토큰의 USER_ID 와 FCM 토큰이 서비스로 간다")
    @Test
    void registerFcmToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(put(FCM_TOKEN_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(FCM_TOKEN_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(fcmTokenService).register(eq(USER_ID), eq(FCM_TOKEN));
    }

    @DisplayName("PUT /users/me/fcm-token - 토큰이 없으면 401")
    @Test
    void registerFcmToken_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(put(FCM_TOKEN_URL).contentType(MediaType.APPLICATION_JSON).content(FCM_TOKEN_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(fcmTokenService);
    }

    @DisplayName("PUT /users/me/fcm-token - FCM 토큰이 비어 있으면 검증 실패로 400")
    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void registerFcmToken_blank(String token) throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                    "token": "%s"
                }
                """.formatted(token);

        /*
         * test
         */
        mockMvc.perform(put(FCM_TOKEN_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message", startsWith("token: ")));
        verifyNoInteractions(fcmTokenService);
    }

    @DisplayName("PUT /users/me/fcm-token - FCM 토큰 필드가 없으면 검증 실패로 400")
    @Test
    void registerFcmToken_missing() throws Exception {

        /*
         * settings
         */
        final String requestJson = """
                {
                }
                """;

        /*
         * test
         */
        mockMvc.perform(put(FCM_TOKEN_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(requestJson))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message", startsWith("token: ")));
        verifyNoInteractions(fcmTokenService);
    }

    @DisplayName("PUT /users/me/fcm-token - 본문이 없으면 400")
    @Test
    void registerFcmToken_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(put(FCM_TOKEN_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(fcmTokenService);
    }

    @DisplayName("PUT /users/me/fcm-token - 서비스의 EMPTY_FCM_TOKEN 은 400 으로 나간다")
    @Test
    void registerFcmToken_emptyFromService() throws Exception {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.EMPTY_FCM_TOKEN)).when(fcmTokenService).register(eq(USER_ID), eq(FCM_TOKEN));

        /*
         * test
         */
        mockMvc.perform(put(FCM_TOKEN_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(FCM_TOKEN_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER106"));
    }

    @DisplayName("PUT /users/me/fcm-token - 사용자가 없으면 404")
    @Test
    void registerFcmToken_userNotFound() throws Exception {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.USER_NOT_FOUND)).when(fcmTokenService).register(eq(USER_ID), eq(FCM_TOKEN));

        /*
         * test
         */
        mockMvc.perform(put(FCM_TOKEN_URL).with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(FCM_TOKEN_REQUEST))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER103"));
    }

    @DisplayName("DELETE /users/me/fcm-token - 성공하면 204 이고 토큰의 USER_ID 가 서비스로 간다")
    @Test
    void removeFcmToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete(FCM_TOKEN_URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNoContent());
        verify(fcmTokenService).remove(eq(USER_ID));
    }

    @DisplayName("DELETE /users/me/fcm-token - 토큰이 없으면 401")
    @Test
    void removeFcmToken_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(delete(FCM_TOKEN_URL))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(fcmTokenService);
    }

    @DisplayName("DELETE /users/me/fcm-token - 사용자가 없으면 404")
    @Test
    void removeFcmToken_userNotFound() throws Exception {

        /*
         * settings
         */
        doThrow(new UserException(UserErrorCode.USER_NOT_FOUND)).when(fcmTokenService).remove(eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(delete(FCM_TOKEN_URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER103"));
    }
}
