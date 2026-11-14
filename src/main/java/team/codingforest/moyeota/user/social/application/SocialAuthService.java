package team.codingforest.moyeota.user.social.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.auth.application.RefreshTokenService;
import team.codingforest.moyeota.user.auth.dto.TokenResponse;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.profile.domain.AgeLimit;
import team.codingforest.moyeota.user.profile.domain.UserProfile;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;
import team.codingforest.moyeota.user.social.domain.GoogleTokenVerifier;
import team.codingforest.moyeota.user.social.domain.SocialProfile;
import team.codingforest.moyeota.user.social.domain.SocialSignupTickets;
import team.codingforest.moyeota.user.social.domain.SocialUser;
import team.codingforest.moyeota.user.social.domain.SocialUsers;
import team.codingforest.moyeota.user.social.dto.SocialLoginResponse;
import team.codingforest.moyeota.user.social.dto.SocialRegisterCommand;
import team.codingforest.moyeota.user.users.domain.IdGenerator;
import team.codingforest.moyeota.user.users.domain.Nickname;
import team.codingforest.moyeota.user.users.domain.PhoneNumber;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.Users;
import team.codingforest.moyeota.user.users.domain.enums.LoginType;
import team.codingforest.moyeota.user.verification.application.SmsVerificationService;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 소셜 로그인과 소셜 회원가입.
 * <p>
 * 소셜 로그인을 통과해도 회원을 바로 만들지 않는다. 처음 온 사람에게는 토큰 대신 가입 대기표를 주고,
 * 휴대폰 인증까지 마친 뒤에 회원 생성과 토큰 발급을 한 번에 한다.
 * 그래서 "토큰을 가진 사람 = 휴대폰 인증을 마친 사람" 이라는 규칙이 로컬 가입과 똑같이 유지된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAuthService {
    private static final Duration SIGNUP_TTL = Duration.ofMinutes(10);

    private final GoogleTokenVerifier googleTokenVerifier;
    private final SocialUsers socialUsers;
    private final SocialSignupTickets signupTickets;
    private final Users users;
    private final UserProfiles userProfiles;
    private final IdGenerator idGenerator;
    private final SmsVerificationService smsVerificationService;
    private final RefreshTokenService refreshTokenService;
    private final team.codingforest.moyeota.user.social.apple.AppleCredentialStore appleCredentials;

    /**
     * 가입한 회원이면 토큰, 처음이면 가입 대기표를 준다.
     * 트랜잭션을 걸지 않는다 - idToken 검사가 구글 공개키를 받아오느라 느려질 수 있어 그동안 DB 연결을 잡지 않는다
     */
    public SocialLoginResponse loginWithGoogle(String idToken) {
        return loginVerified(googleTokenVerifier.verify(idToken));
    }

    public SocialLoginResponse loginVerified(SocialProfile profile) {

        Optional<SocialUser> socialUser = socialUsers.find(profile.socialType(), profile.socialId());
        if (socialUser.isEmpty()) {
            if (profile.email() == null || profile.email().isBlank()) throw new UserException(UserErrorCode.SOCIAL_TOKEN_INVALID);
            return SocialLoginResponse.signupRequired(issueSignupTicket(profile), profile);
        }

        User user = users.findById(socialUser.get().getUserId());
        if (user.isDeleted()) {   // 탈퇴하면 social_user 행도 지우므로 정상 흐름에서는 오지 않는다
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }
        return SocialLoginResponse.login(refreshTokenService.issue(user.getId(), user.getPublicId()));
    }

    @Transactional // users / social_user / profile 3개 테이블 원자 저장 - LocalUserService.register 와 같은 순서
    public TokenResponse register(SocialRegisterCommand command) {
        // 부수효과가 없는 입력 검증이라 대기표·문자 인증 확인보다 먼저 본다
        AgeLimit.check(command.birthDate(), Instant.now());

        SocialProfile profile = findSignupTicket(command.signupToken());

        // 인증 안 된 요청이면 중복 조회 전에 끊음
        smsVerificationService.checkVerified(command.verificationToken(), command.phoneNumber());

        if (socialUsers.exists(profile.socialType(), profile.socialId())) {
            throw new UserException(UserErrorCode.SOCIAL_ALREADY_REGISTERED);
        }

        if (userProfiles.existsByPhoneNumber(new PhoneNumber(command.phoneNumber()).value())) {
            throw new UserException(UserErrorCode.PHONE_NUMBER_DUPLICATED);
        }

        if (users.existsByNickname(new Nickname(command.nickname()).value())) {
            throw new UserException(UserErrorCode.NICKNAME_DUPLICATED);
        }

        User user = users.save(User.from(idGenerator.generate(), LoginType.SOCIAL, command.nickname()));

        try {
            socialUsers.register(user.getId(), profile.socialType(), profile.socialId());
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new UserException(UserErrorCode.SOCIAL_ALREADY_REGISTERED);
        }
        if (profile.socialType() == team.codingforest.moyeota.user.social.domain.enums.SocialType.APPLE) appleCredentials.attachSignup(command.signupToken(), user.getId());

        // 이메일은 요청이 아니라 대기표에서 꺼낸다 - 소셜 서비스가 확인해 준 값만 쓴다
        userProfiles.save(UserProfile.of(user.getId(), command.name(), command.birthDate(), command.phoneNumber(), command.gender(), profile.email()));

        // 비밀번호가 없어 가입 뒤 따로 로그인할 수단이 소셜 로그인뿐이다. 그래서 로컬 가입과 달리 가입과 함께 토큰을 준다
        TokenResponse tokens = refreshTokenService.issue(user.getId(), user.getPublicId());

        // 저장까지 끝난 뒤에 지움 - 닉네임 중복 등으로 실패하면 소셜 로그인·문자 인증을 다시 하지 않고 재시도 가능
        smsVerificationService.consume(command.verificationToken());
        deleteSignupTicket(command.signupToken());

        log.info("소셜 회원가입 userId={} socialType={}", user.getId(), profile.socialType());
        return tokens;
    }

    private String issueSignupTicket(SocialProfile profile) {
        String signupToken = UUID.randomUUID().toString();
        try {
            signupTickets.save(signupToken, profile, SIGNUP_TTL);
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
        return signupToken;
    }

    private SocialProfile findSignupTicket(String signupToken) {
        try {
            return signupTickets.find(signupToken)
                    .orElseThrow(() -> new UserException(UserErrorCode.SOCIAL_SIGNUP_EXPIRED));
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    // 실패해도 10분 뒤 만료되고, 같은 소셜 계정의 재가입은 social_user UNIQUE 가 막음
    private void deleteSignupTicket(String signupToken) {
        try {
            signupTickets.delete(signupToken);
        } catch (DataAccessException e) {
            log.warn("가입 대기표 삭제 실패, TTL 만료에 맡김", e);
        }
    }

    private UserException unavailable(DataAccessException e) {
        log.warn("가입 대기표 Redis 실패", e);
        return new UserException(UserErrorCode.SOCIAL_UNAVAILABLE);
    }
}
