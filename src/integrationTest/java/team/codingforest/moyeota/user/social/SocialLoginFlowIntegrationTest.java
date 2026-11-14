package team.codingforest.moyeota.user.social;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import team.codingforest.moyeota.user.auth.application.AuthService;
import team.codingforest.moyeota.user.auth.dto.TokenResponse;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.deletion.application.UserDeletionService;
import team.codingforest.moyeota.user.profile.domain.UserProfile;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;
import team.codingforest.moyeota.user.social.application.SocialAuthService;
import team.codingforest.moyeota.user.social.domain.GoogleTokenVerifier;
import team.codingforest.moyeota.user.social.domain.SocialProfile;
import team.codingforest.moyeota.user.social.domain.enums.SocialType;
import team.codingforest.moyeota.user.social.dto.SocialLoginResponse;
import team.codingforest.moyeota.user.social.dto.SocialLoginStatus;
import team.codingforest.moyeota.user.social.dto.SocialRegisterCommand;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.Users;
import team.codingforest.moyeota.user.users.domain.enums.Gender;
import team.codingforest.moyeota.user.users.domain.enums.LoginType;
import team.codingforest.moyeota.user.verification.application.SmsVerificationService;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 *  구글 로그인 → 가입 대기표 → 문자 인증 → 소셜 가입 → 재로그인·재발급 → 탈퇴를 실제 DB·Redis 로 한 번에 본다.
 *  진짜 구글 idToken 은 테스트에서 만들 수 없어 검사기(GoogleTokenVerifier)만 바꿔 끼운다
 */
@SpringBootTest(properties = {"moyeota.sms.provider=log", "moyeota.sms.fixed-code=000000",
        "moyeota.sms.review.phones=", "moyeota.sms.review.code="})
class SocialLoginFlowIntegrationTest {
    private static final String ID_TOKEN = "google-id-token";
    private static final String SMS_CODE = "000000";

    @MockitoBean GoogleTokenVerifier googleTokenVerifier;

    @Autowired SocialAuthService socialAuthService;
    @Autowired SmsVerificationService smsVerificationService;
    @Autowired AuthService authService;
    @Autowired Users users;
    @Autowired UserProfiles userProfiles;
    @Autowired UserDeletionService userDeletionService;

    @Test
    void 처음_온_구글_계정은_휴대폰_인증을_거쳐_가입하고_로그인_재발급_탈퇴까지_된다() {
        long base = System.currentTimeMillis() % 100_000_000L;
        String phone = "010" + String.format("%08d", base);
        String nickname = "soc" + base % 1_000_000;
        String email = "social" + base + "@gmail.com";
        given(googleTokenVerifier.verify(ID_TOKEN))
                .willReturn(new SocialProfile(SocialType.GOOGLE, "google-sub-" + base, email, "홍길동"));

        // ① 처음 온 사람 - 토큰 대신 가입 대기표
        SocialLoginResponse first = socialAuthService.loginWithGoogle(ID_TOKEN);
        assertThat(first.status()).isEqualTo(SocialLoginStatus.SIGNUP_REQUIRED);
        assertThat(first.accessToken()).isNull();
        assertThat(first.email()).isEqualTo(email);

        // ② 휴대폰 인증 없이 가입하면 거절된다
        assertThatThrownBy(() -> socialAuthService.register(가입(first.signupToken(), "no-such-token", phone, nickname)))
                .isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.SMS_NOT_VERIFIED);

        // ③ 휴대폰 인증 뒤 가입 - 회원 생성과 토큰 발급이 함께 된다
        smsVerificationService.send(phone);
        String verificationToken = smsVerificationService.verify(phone, SMS_CODE);
        TokenResponse tokens = socialAuthService.register(가입(first.signupToken(), verificationToken, phone, nickname));

        AuthenticatedPrincipal principal = authService.authenticate(tokens.accessToken());
        User user = users.findById(principal.userId());
        UserProfile profile = userProfiles.findByUserId(user.getId()).orElseThrow();
        assertThat(user.getLoginType()).isEqualTo(LoginType.SOCIAL);
        assertThat(user.getNickname()).isEqualTo(nickname);
        assertThat(profile.getEmail()).as("이메일은 구글이 확인해 준 값").isEqualTo(email);
        assertThat(profile.getPhoneNumber()).isEqualTo(phone);

        // ④ 쓴 대기표로는 다시 가입할 수 없다
        assertThatThrownBy(() -> socialAuthService.register(가입(first.signupToken(), verificationToken, phone, nickname)))
                .isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.SOCIAL_SIGNUP_EXPIRED);

        // ⑤ 같은 구글 계정으로 다시 오면 바로 로그인된다
        SocialLoginResponse second = socialAuthService.loginWithGoogle(ID_TOKEN);
        assertThat(second.status()).isEqualTo(SocialLoginStatus.LOGIN);
        assertThat(second.signupToken()).isNull();
        assertThat(authService.authenticate(second.accessToken()).userId()).isEqualTo(user.getId());

        // ⑥ 비밀번호가 없는 소셜 회원도 토큰을 재발급받는다 (social_user 행을 잠근다)
        TokenResponse reissued = authService.reissue(tokens.refreshToken());
        assertThat(authService.authenticate(reissued.accessToken()).userId()).isEqualTo(user.getId());

        // ⑦ 소셜 회원은 비밀번호 없이 탈퇴한다 - 토큰이 막히고 소셜 연동도 끊긴다
        userDeletionService.delete(user.getId(), null);
        assertThatThrownBy(() -> authService.authenticate(reissued.accessToken()))
                .isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.TOKEN_INVALID);
        assertThatThrownBy(() -> authService.reissue(reissued.refreshToken()))
                .isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.TOKEN_INVALID);

        // ⑧ 같은 구글 계정으로 다시 오면 처음 온 사람이고, 같은 번호는 30일 재가입 제한에 걸린다
        assertThat(socialAuthService.loginWithGoogle(ID_TOKEN).status()).isEqualTo(SocialLoginStatus.SIGNUP_REQUIRED);
        assertThatThrownBy(() -> smsVerificationService.send(phone))
                .isInstanceOf(UserException.class)
                .extracting("errorCode").isEqualTo(UserErrorCode.REJOIN_RESTRICTED);
    }

    private SocialRegisterCommand 가입(String signupToken, String verificationToken, String phone, String nickname) {
        return new SocialRegisterCommand(signupToken, verificationToken, phone, nickname, "홍길동",
                Instant.parse("2000-01-01T00:00:00Z"), Gender.MALE);
    }
}
