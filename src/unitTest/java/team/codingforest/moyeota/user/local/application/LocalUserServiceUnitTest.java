package team.codingforest.moyeota.user.local.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.user.local.domain.LocalUsers;
import team.codingforest.moyeota.user.local.domain.PasswordHasher;
import team.codingforest.moyeota.user.local.dto.MyProfileResponse;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;
import team.codingforest.moyeota.user.sanction.domain.NoShowSanction;
import team.codingforest.moyeota.user.sanction.domain.NoShowSanctions;
import team.codingforest.moyeota.user.users.application.ProfileImages;
import team.codingforest.moyeota.user.users.domain.IdGenerator;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.Users;
import team.codingforest.moyeota.user.users.domain.enums.LoginType;
import team.codingforest.moyeota.user.verification.application.SmsVerificationService;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 *  내 프로필의 노쇼 제재 필드. 이름·이미지 폴백은 LocalUserServiceTest 가 본다
 */
@ExtendWith(MockitoExtension.class)
class LocalUserServiceUnitTest {
    private static final Long USER_ID = 1L;
    private static final UUID PUBLIC_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String IMAGE_URL = "https://cdn.example.com/profile/presets/default.png";

    @Mock
    private Users users;
    @Mock
    private LocalUsers localUsers;
    @Mock
    private UserProfiles userProfiles;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private SmsVerificationService smsVerificationService;
    @Mock
    private ProfileImages profileImages;
    @Mock
    private NoShowSanctions noShowSanctions;
    @InjectMocks
    private LocalUserService service;

    private void givenUser() {
        doReturn(User.restore(USER_ID, PUBLIC_ID, "길동이", null, LoginType.LOCAL, null, Instant.EPOCH, null)).when(users).findById(eq(USER_ID));
        doReturn(IMAGE_URL).when(profileImages).toUrl(eq(null));
    }

    @DisplayName("getProfile - 제한 중이면 감소를 반영한 누적 횟수와 제한 끝 시각을 싣는다")
    @Test
    void getProfile_restricted() {

        /*
         * settings
         */
        Instant until = Instant.now().plus(Duration.ofDays(1));
        givenUser();
        doReturn(Optional.of(new NoShowSanction(2, Instant.now().minus(Duration.ofDays(1)), until))).when(noShowSanctions).find(eq(USER_ID));

        /*
         * test
         */
        MyProfileResponse response = service.getProfile(USER_ID);

        /*
         * verify
         */
        assertThat(response.uuid()).isEqualTo(PUBLIC_ID);
        assertThat(response.name()).isEqualTo("길동이");
        assertThat(response.imageUrl()).isEqualTo(IMAGE_URL);
        assertThat(response.noShowCount()).isEqualTo(2);
        assertThat(response.restrictedUntil()).isEqualTo(until);
        verifyNoInteractions(userProfiles);
    }

    @DisplayName("getProfile - 제한이 끝났으면 restrictedUntil 은 null 이고 30일이 지난 만큼 누적 횟수가 줄어 있다")
    @Test
    void getProfile_expiredAndDecayed() {

        /*
         * settings
         */
        givenUser();
        doReturn(Optional.of(new NoShowSanction(2, Instant.now().minus(Duration.ofDays(31)), Instant.now().minus(Duration.ofDays(29)))))
                .when(noShowSanctions).find(eq(USER_ID));

        /*
         * test
         */
        MyProfileResponse response = service.getProfile(USER_ID);

        /*
         * verify
         */
        assertThat(response.noShowCount()).isEqualTo(1);
        assertThat(response.restrictedUntil()).isNull();
    }

    @DisplayName("getProfile - 노쇼가 없었으면 0 회, 제한 없음")
    @Test
    void getProfile_noSanction() {

        /*
         * settings
         */
        givenUser();
        doReturn(Optional.of(NoShowSanction.NONE)).when(noShowSanctions).find(eq(USER_ID));

        /*
         * test
         */
        MyProfileResponse response = service.getProfile(USER_ID);

        /*
         * verify
         */
        assertThat(response.noShowCount()).isZero();
        assertThat(response.restrictedUntil()).isNull();
    }

    @DisplayName("getProfile - 제재 행을 읽지 못해도(empty) 0 회, 제한 없음으로 응답한다")
    @Test
    void getProfile_sanctionMissing() {

        /*
         * settings
         */
        givenUser();
        doReturn(Optional.empty()).when(noShowSanctions).find(eq(USER_ID));

        /*
         * test
         */
        MyProfileResponse response = service.getProfile(USER_ID);

        /*
         * verify
         */
        assertThat(response.noShowCount()).isZero();
        assertThat(response.restrictedUntil()).isNull();
    }
}
