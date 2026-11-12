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
import team.codingforest.moyeota.user.local.domain.LocalUsers;
import team.codingforest.moyeota.user.local.domain.PasswordHasher;
import team.codingforest.moyeota.user.profile.domain.UserProfile;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;
import team.codingforest.moyeota.user.users.application.ProfileImages;
import team.codingforest.moyeota.user.users.domain.IdGenerator;
import team.codingforest.moyeota.user.users.domain.Users;
import team.codingforest.moyeota.user.users.domain.enums.Gender;
import team.codingforest.moyeota.user.verification.application.SmsVerificationService;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 *  문자 인증으로 아이디 찾기. 가입·프로필은 LocalUserServiceTest 가 본다
 */
@ExtendWith(MockitoExtension.class)
class LocalUserServiceUnitTest {
    private static final Long USER_ID = 1L;

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
    @InjectMocks
    private LocalUserService service;

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
