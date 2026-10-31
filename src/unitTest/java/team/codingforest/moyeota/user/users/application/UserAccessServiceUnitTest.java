package team.codingforest.moyeota.user.users.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.Users;
import team.codingforest.moyeota.user.users.domain.enums.LoginType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserAccessServiceUnitTest {
    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final UUID PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String NICKNAME = "모여타";
    private static final String IMAGE_KEY = "profile/presets/03.png";
    private static final String IMAGE_URL = "https://cdn.example.com/profile/presets/03.png";
    private static final String DEFAULT_IMAGE_URL = "https://cdn.example.com/profile/default.png";
    private static final Long BADGE_ID = 7L;

    @Mock
    private Users users;
    @Mock
    private ProfileImages profileImages;
    @InjectMocks
    private UserAccessService service;

    private static User user(Long id, UUID publicId, String nickname, String imageKey, Long badgeId) {
        return User.restore(id, publicId, nickname, imageKey, LoginType.LOCAL, badgeId, null, Instant.EPOCH, null);
    }

    private static User deletedUser() {
        User user = user(USER_ID, PUBLIC_ID, NICKNAME, IMAGE_KEY, BADGE_ID);
        user.delete(Instant.EPOCH);
        return user;
    }

    @DisplayName("findFcmTokens - 저장소 결과를 그대로 반환")
    @Test
    void findFcmTokens() {

        /*
         * settings
         */
        final List<Long> userIds = List.of(USER_ID, OTHER_USER_ID);
        doReturn(Map.of(USER_ID, "token-1")).when(users).findFcmTokens(eq(userIds));

        /*
         * test
         */
        Map<Long, String> tokens = service.findFcmTokens(userIds);

        /*
         * verify
         */
        assertThat(tokens).containsOnly(Map.entry(USER_ID, "token-1"));
    }

    @DisplayName("findFcmTokens - 토큰이 하나도 없으면 빈 맵")
    @Test
    void findFcmTokens_empty() {

        /*
         * settings
         */
        doReturn(Map.of()).when(users).findFcmTokens(eq(List.of(USER_ID)));

        /*
         * test
         */
        Map<Long, String> tokens = service.findFcmTokens(List.of(USER_ID));

        /*
         * verify
         */
        assertThat(tokens).isEmpty();
    }

    @DisplayName("findMemberSummaries - 사용자별로 공개 ID, 닉네임, 이미지 URL, 배지를 담아 반환")
    @Test
    void findMemberSummaries() {

        /*
         * settings
         */
        final List<Long> userIds = List.of(USER_ID, OTHER_USER_ID);
        doReturn(List.of(
                user(USER_ID, PUBLIC_ID, NICKNAME, IMAGE_KEY, BADGE_ID),
                user(OTHER_USER_ID, OTHER_PUBLIC_ID, "동승자", null, null)
        )).when(users).findAllByIds(eq(userIds));
        doReturn(IMAGE_URL).when(profileImages).toUrl(eq(IMAGE_KEY));
        doReturn(DEFAULT_IMAGE_URL).when(profileImages).toUrl(eq(null));

        /*
         * test
         */
        Map<Long, MemberSummary> summaries = service.findMemberSummaries(userIds);

        /*
         * verify
         */
        assertThat(summaries).containsOnly(
                Map.entry(USER_ID, new MemberSummary(PUBLIC_ID, NICKNAME, IMAGE_URL, BADGE_ID)),
                Map.entry(OTHER_USER_ID, new MemberSummary(OTHER_PUBLIC_ID, "동승자", DEFAULT_IMAGE_URL, null))
        );
    }

    @DisplayName("findMemberSummaries - 탈퇴자는 표시 이름으로 바꾸고 배지를 숨긴다")
    @Test
    void findMemberSummaries_deleted() {

        /*
         * settings
         */
        doReturn(List.of(deletedUser())).when(users).findAllByIds(eq(List.of(USER_ID)));
        doReturn(DEFAULT_IMAGE_URL).when(profileImages).toUrl(eq(null));

        /*
         * test
         */
        Map<Long, MemberSummary> summaries = service.findMemberSummaries(List.of(USER_ID));

        /*
         * verify
         */
        assertThat(summaries).containsOnly(
                Map.entry(USER_ID, new MemberSummary(PUBLIC_ID, User.DELETED_DISPLAY_NAME, DEFAULT_IMAGE_URL, null))
        );
    }

    @DisplayName("findMemberSummaries - 닉네임을 정하지 않은 사용자는 닉네임 null")
    @Test
    void findMemberSummaries_noNickname() {

        /*
         * settings
         */
        doReturn(List.of(user(USER_ID, PUBLIC_ID, null, null, null))).when(users).findAllByIds(eq(List.of(USER_ID)));
        doReturn(DEFAULT_IMAGE_URL).when(profileImages).toUrl(eq(null));

        /*
         * test
         */
        Map<Long, MemberSummary> summaries = service.findMemberSummaries(List.of(USER_ID));

        /*
         * verify
         */
        assertThat(summaries.get(USER_ID).nickname()).isNull();
    }

    @DisplayName("findMemberSummaries - 찾은 사용자가 없으면 빈 맵이고 이미지 변환을 하지 않는다")
    @Test
    void findMemberSummaries_noUsers() {

        /*
         * settings
         */
        doReturn(List.of()).when(users).findAllByIds(eq(List.of(USER_ID)));

        /*
         * test
         */
        Map<Long, MemberSummary> summaries = service.findMemberSummaries(List.of(USER_ID));

        /*
         * verify
         */
        assertThat(summaries).isEmpty();
        verifyNoInteractions(profileImages);
    }

    @DisplayName("findNickname - 있으면 닉네임을 반환하고 예외를 던지는 findById 는 쓰지 않는다")
    @Test
    void findNickname() {

        /*
         * settings
         */
        doReturn(List.of(user(USER_ID, PUBLIC_ID, NICKNAME, null, null))).when(users).findAllByIds(eq(List.of(USER_ID)));

        /*
         * test
         */
        Optional<String> nickname = service.findNickname(USER_ID);

        /*
         * verify
         */
        assertThat(nickname).contains(NICKNAME);
        verify(users, never()).findById(any());
    }

    @DisplayName("findNickname - 사용자가 없으면 empty")
    @Test
    void findNickname_noUser() {

        /*
         * settings
         */
        doReturn(List.of()).when(users).findAllByIds(eq(List.of(USER_ID)));

        /*
         * test
         */
        Optional<String> nickname = service.findNickname(USER_ID);

        /*
         * verify
         */
        assertThat(nickname).isEmpty();
        verify(users, never()).findById(any());
    }

    @DisplayName("findNickname - 탈퇴자면 표시 이름")
    @Test
    void findNickname_deleted() {

        /*
         * settings
         */
        doReturn(List.of(deletedUser())).when(users).findAllByIds(eq(List.of(USER_ID)));

        /*
         * test
         */
        Optional<String> nickname = service.findNickname(USER_ID);

        /*
         * verify
         */
        assertThat(nickname).contains(User.DELETED_DISPLAY_NAME);
    }

    @DisplayName("findNickname - 닉네임을 정하지 않았으면 empty")
    @Test
    void findNickname_noNickname() {

        /*
         * settings
         */
        doReturn(List.of(user(USER_ID, PUBLIC_ID, null, null, null))).when(users).findAllByIds(eq(List.of(USER_ID)));

        /*
         * test
         */
        Optional<String> nickname = service.findNickname(USER_ID);

        /*
         * verify
         */
        assertThat(nickname).isEmpty();
    }
}
