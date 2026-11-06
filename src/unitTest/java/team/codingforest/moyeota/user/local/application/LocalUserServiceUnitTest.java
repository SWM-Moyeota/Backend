package team.codingforest.moyeota.user.local.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.local.domain.LocalUser;
import team.codingforest.moyeota.user.profile.domain.UserProfile;
import team.codingforest.moyeota.user.users.domain.enums.Gender;
import team.codingforest.moyeota.user.local.domain.LocalUsers;
import team.codingforest.moyeota.user.local.domain.PasswordHasher;
import team.codingforest.moyeota.user.local.dto.MyProfileResponse;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;
import team.codingforest.moyeota.user.sanction.domain.NoShow;
import team.codingforest.moyeota.user.sanction.domain.NoShowSanction;
import team.codingforest.moyeota.user.sanction.domain.NoShowSanctions;
import team.codingforest.moyeota.user.sanction.domain.NoShows;
import team.codingforest.moyeota.user.users.application.ProfileImages;
import team.codingforest.moyeota.user.users.domain.IdGenerator;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.Users;
import team.codingforest.moyeota.user.users.domain.enums.LoginType;
import team.codingforest.moyeota.user.verification.application.SmsVerificationService;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
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
    @Mock
    private NoShows noShows;
    @InjectMocks
    private LocalUserService service;

    private void givenUser() {
        doReturn(User.restore(USER_ID, PUBLIC_ID, "길동이", null, LoginType.LOCAL, null, Instant.EPOCH, null)).when(users).findById(eq(USER_ID));
        doReturn(IMAGE_URL).when(profileImages).toUrl(eq(null));
    }

    @DisplayName("getProfile - 제한 중이면 지금 노쇼 횟수와 제한 끝 시각을 싣는다")
    @Test
    void getProfile_restricted() {

        /*
         * settings
         */
        Instant until = Instant.now().plus(Duration.ofDays(1));
        givenUser();
        doReturn(Optional.of(new NoShowSanction(until))).when(noShowSanctions).find(eq(USER_ID));
        doReturn(List.of(new NoShow(Instant.now().minus(Duration.ofDays(3)), null), new NoShow(Instant.now().minus(Duration.ofHours(1)), null)))
                .when(noShows).findUnexpired(eq(USER_ID), any(Instant.class));

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

    @DisplayName("getProfile - 제한이 끝났으면 restrictedUntil 은 null 이고, 무효로 돌린 노쇼는 노쇼 횟수에 들어가지 않는다")
    @Test
    void getProfile_expiredAndVoided() {

        /*
         * settings
         */
        givenUser();
        doReturn(Optional.of(new NoShowSanction(Instant.now().minus(Duration.ofDays(1))))).when(noShowSanctions).find(eq(USER_ID));
        doReturn(List.of(new NoShow(Instant.now().minus(Duration.ofDays(5)), null),
                new NoShow(Instant.now().minus(Duration.ofDays(3)), Instant.now().minus(Duration.ofDays(2)))))
                .when(noShows).findUnexpired(eq(USER_ID), any(Instant.class));

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

    private static final String PHONE = "01012345678";

    private void givenLocalAccount() {
        doReturn(Optional.of(UserProfile.of(USER_ID, "홍길동", Instant.EPOCH, PHONE, Gender.MALE, "hong@example.com")))
                .when(userProfiles).findByPhoneNumber(eq(PHONE));
        doReturn(Optional.of(LocalUser.from(USER_ID, "hong123", "hashed", Instant.EPOCH, Instant.EPOCH)))
                .when(localUsers).findByUserId(eq(USER_ID));
    }

    @DisplayName("아이디 찾기 발송 - 가입된 번호면 정규화한 번호로 문자를 보낸다")
    @Test
    void sendFindLoginIdCode_registered() {
        givenLocalAccount();

        service.sendFindLoginIdCode("010-1234-5678");

        verify(smsVerificationService).sendCode(PHONE);
    }

    @DisplayName("아이디 찾기 발송 - 미가입 번호면 문자 없이 거절한다")
    @Test
    void sendFindLoginIdCode_notRegistered() {
        doReturn(Optional.empty()).when(userProfiles).findByPhoneNumber(eq(PHONE));

        assertThatThrownBy(() -> service.sendFindLoginIdCode(PHONE))
                .isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.PHONE_NOT_REGISTERED);
        verify(smsVerificationService, never()).sendCode(any());
    }

    @DisplayName("아이디 찾기 발송 - 로컬 계정이 없는 번호도 미가입으로 처리한다")
    @Test
    void sendFindLoginIdCode_noLocalAccount() {
        doReturn(Optional.of(UserProfile.of(USER_ID, "홍길동", Instant.EPOCH, PHONE, Gender.MALE, "hong@example.com")))
                .when(userProfiles).findByPhoneNumber(eq(PHONE));
        doReturn(Optional.empty()).when(localUsers).findByUserId(eq(USER_ID));

        assertThatThrownBy(() -> service.sendFindLoginIdCode(PHONE))
                .isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.PHONE_NOT_REGISTERED);
        verify(smsVerificationService, never()).sendCode(any());
    }

    @DisplayName("아이디 찾기 확인 - 코드가 맞으면 아이디를 돌려준다")
    @Test
    void findLoginId_success() {
        givenLocalAccount();

        assertThat(service.findLoginId(PHONE, "123456")).isEqualTo("hong123");
    }

    @DisplayName("아이디 찾기 확인 - 코드가 틀리면 계정을 조회하지 않는다")
    @Test
    void findLoginId_wrongCode() {
        doThrow(new UserException(UserErrorCode.SMS_CODE_MISMATCH))
                .when(smsVerificationService).checkCode(eq(PHONE), eq("000000"));

        assertThatThrownBy(() -> service.findLoginId(PHONE, "000000"))
                .isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.SMS_CODE_MISMATCH);
        verifyNoInteractions(userProfiles, localUsers);
    }

}
