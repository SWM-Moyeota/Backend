package team.codingforest.moyeota.user.local.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.auth.dto.AuthenticatedUser;
import team.codingforest.moyeota.user.auth.dto.UserLoginCommand;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.common.masking.Masking;
import team.codingforest.moyeota.user.local.domain.LocalUser;
import team.codingforest.moyeota.user.local.domain.LocalUsers;
import team.codingforest.moyeota.user.local.domain.PasswordHasher;
import team.codingforest.moyeota.user.local.domain.PasswordPolicy;
import team.codingforest.moyeota.user.local.dto.MyProfileResponse;
import team.codingforest.moyeota.user.local.dto.UserRegisterCommand;
import team.codingforest.moyeota.user.local.dto.UserResponse;
import team.codingforest.moyeota.user.profile.domain.AgeLimit;
import team.codingforest.moyeota.user.profile.domain.UserProfile;
import team.codingforest.moyeota.user.profile.domain.UserProfiles;
import team.codingforest.moyeota.user.users.application.ProfileImages;
import team.codingforest.moyeota.user.users.domain.IdGenerator;
import team.codingforest.moyeota.user.users.domain.Nickname;
import team.codingforest.moyeota.user.users.domain.PhoneNumber;
import team.codingforest.moyeota.user.users.domain.User;
import team.codingforest.moyeota.user.users.domain.Users;
import team.codingforest.moyeota.user.users.domain.enums.LoginType;
import team.codingforest.moyeota.user.verification.application.SmsVerificationService;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LocalUserService {
    private final Users users;
    private final LocalUsers localUsers;
    private final UserProfiles userProfiles;
    private final PasswordHasher passwordHasher;
    private final IdGenerator idGenerator;
    private final SmsVerificationService smsVerificationService;
    private final ProfileImages profileImages;

    @Transactional // users / local_user / profile 3개 테이블 원자 저장
    public UserResponse register(UserRegisterCommand command) {
        PasswordPolicy.validate(command.password());

        // 만 14세 미만은 가입 불가 - 부수효과가 없는 입력 검증이라 문자 인증 확인·중복 조회보다 먼저 본다
        AgeLimit.check(command.birthDate(), Instant.now());

        // 인증 안 된 요청이면 중복 조회 전에 끊음
        smsVerificationService.checkVerified(command.verificationToken(), command.phoneNumber());

        if(localUsers.existsByLoginId(command.loginId())) {
            throw new UserException(UserErrorCode.LOGIN_ID_DUPLICATED);
        }

        if(existsByPhoneNumber(command.phoneNumber())) {
            throw new UserException(UserErrorCode.PHONE_NUMBER_DUPLICATED);
        }

        if(existsByNickname(command.nickname())) {
            throw new UserException(UserErrorCode.NICKNAME_DUPLICATED);
        }

        User user = users.save(User.from(idGenerator.generate(), LoginType.LOCAL, command.nickname()));

        localUsers.register(user.getId(), command.loginId(), passwordHasher.hash(command.password()));

        UserProfile userProfile = UserProfile.of(user.getId(), command.name(), command.birthDate(), command.phoneNumber(), command.gender(), command.email());

        userProfiles.save(userProfile);

        // 저장까지 끝난 뒤에 지움 - 닉네임 중복 등으로 실패하면 인증을 다시 하지 않고 재시도 가능
        smsVerificationService.consume(command.verificationToken());

        return new UserResponse(user.getPublicId(), user.getNickname(), profileImages.toUrl(user.getImageUrl()));
    }

    @Transactional
    public AuthenticatedUser authenticate(UserLoginCommand command) {
        LocalUser localUser = localUsers.findByLoginIdForUpdate(command.loginId());
        if (!passwordHasher.matches(command.password(), localUser.getPassword())) {
            throw new UserException(UserErrorCode.LOGIN_FAILED);
        }

        User user = users.findById(localUser.getUserId());

        return new AuthenticatedUser(user.getId(), user.getPublicId());
    }

    // 같은 users 행을 두 번 읽는다 - 한 트랜잭션이라 두 번째는 영속성 컨텍스트에서 나온다
    @Transactional(readOnly = true)
    public MyProfileResponse getProfile(Long userId) {
        User user = users.findById(userId);
        // 닉네임 미설정(가입 직후 기본 상태)이면 가린 실명으로 폴백한다
        String name = user.getNickname() != null ? user.getNickname()
                : userProfiles.findByUserId(userId).map(UserProfile::getName).map(Masking::name).orElse(null);

        String loginId = localUsers.findByUserId(userId).map(LocalUser::getLoginId).orElse(null);

        return new MyProfileResponse(user.getPublicId(), name, profileImages.toUrl(user.getImageUrl()), 0, null, loginId);
    }

    /*
     ** 인증번호 보내기
     * 1. 번호를 01012345678형식으로 정리합니다.
     * 2. 가입된 번호인지 확인합니다. 미가입이면 여기서 에러가 나서 문자가 나가지 않습니다.
     * 3. SmsVerificationService의 sendCode()로 문자를 보냅니다.
     */
    public void sendFindLoginIdCode(String rawPhoneNumber){
        String phone=new PhoneNumber(rawPhoneNumber).value();
        findLocalUserByPhone(phone); //미가입이면 문자 비용을 쓰기 전에 거절
        smsVerificationService.sendCode(phone);
    }


    /*
     ** 아이디 찾기
     * 1. SmsVerificationService에서 만든 checkCode()로 인증번호를 확인합니다. 틀리면 여기서 에러가 납니다.
     * 2. 번호로 계정을 찾아 아이디를 돌려줍니다.
     */
    public String findLoginId(String rawPhoneNumber, String code){
        smsVerificationService.checkCode(rawPhoneNumber, code); //코드가 맞아야 계정을 조회
        String loginId = findLocalUserByPhone(new PhoneNumber(rawPhoneNumber).value()).getLoginId();
        // 찾은 아이디로 곧바로 비밀번호를 재설정할 수 있게 재전송 대기를 푼다.
        // 풀지 않으면 재설정 요청이 1분 동안 429(USER306)로 막힌다 - 두 기능이 같은 대기 칸(sms:cooldown)을 쓴다
        smsVerificationService.allowResend(rawPhoneNumber);
        return loginId;
    }

    /*
     ** 번호로 계정 찾기
     * 1. 아이디는 local_user 테이블에 있어서, 번호로 프로필을 찾은 뒤, 그 회원 번호로 계정을 한 번 더 찾습니다.
     * 번호 -> user_profile에서 프로필 찾기 -> userId로 local_user에서 계정 찾기
     */
    private LocalUser findLocalUserByPhone(String phone){
        return userProfiles.findByPhoneNumber(phone)
                .flatMap(profile->localUsers.findByUserId(profile.getUserId()))
                .orElseThrow(()->new UserException(UserErrorCode.PHONE_NOT_REGISTERED));
    }

    @Transactional(readOnly = true)
    public boolean existsByPhoneNumber(String rawPhoneNumber) {
        return userProfiles.existsByPhoneNumber(new PhoneNumber(rawPhoneNumber).value());
    }

    /** 정규화(공백 제거) 후 비교 - phone/check 와 같은 원칙 */
    @Transactional(readOnly = true)
    public boolean existsByNickname(String rawNickname) {
        return users.existsByNickname(new Nickname(rawNickname).value());
    }
}