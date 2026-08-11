package team.codingforest.moyeota.auth.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import team.codingforest.moyeota.auth.dto.SignupRequest;
import team.codingforest.moyeota.auth.entity.LocalUser;
import team.codingforest.moyeota.auth.entity.SocialUser;
import team.codingforest.moyeota.auth.entity.User;
import team.codingforest.moyeota.auth.entity.UserProfile;
import team.codingforest.moyeota.auth.entity.enums.Gender;
import team.codingforest.moyeota.auth.entity.enums.LoginType;
import team.codingforest.moyeota.auth.entity.enums.Role;
import team.codingforest.moyeota.auth.entity.enums.SocialType;
import team.codingforest.moyeota.auth.repository.LocalUserRepository;
import team.codingforest.moyeota.auth.repository.SocialUserRepository;
import team.codingforest.moyeota.auth.repository.UserProfileRepository;
import team.codingforest.moyeota.auth.repository.UserRepository;

import java.util.UUID;

@Service
public class UserService {

    //Spring Security의 권한 문자열. Role(PASSENGER/DRIVER)은 서비스 안에서의 역할이고
    //이건 "로그인한 사용자냐"를 나타내는 보안 권한이라 서로 다른 개념이다.
    //SecurityConfig의 hasRole("USER")가 이 값을 본다.
    public static final String SECURITY_ROLE = "ROLE_USER";

    private final UserRepository userRepository;
    private final SocialUserRepository socialUserRepository;
    private final UserProfileRepository userProfileRepository;
    private final LocalUserRepository localUserRepository;
    private final PasswordEncoder passwordEncoder;

    //자기 자신을 주입받는다.
    //@Transactional은 프록시가 걸어주는 것이라 같은 클래스 안에서 그냥 호출하면 트랜잭션이 시작되지 않는다.
    //아래 findOrCreateSocialUser는 트랜잭션 밖에 있어야 하고(실패 후 다시 조회해야 하므로),
    //createSocialUser는 트랜잭션 안에 있어야 해서 프록시를 거쳐 부른다.
    private final UserService self;

    public UserService(UserRepository userRepository,
                       SocialUserRepository socialUserRepository,
                       UserProfileRepository userProfileRepository,
                       LocalUserRepository localUserRepository,
                       PasswordEncoder passwordEncoder,
                       @Lazy UserService self) {

        this.userRepository = userRepository;
        this.socialUserRepository = socialUserRepository;
        this.userProfileRepository = userProfileRepository;
        this.localUserRepository = localUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.self = self;
    }

    /*
    소셜 로그인의 입구. 조회 -> 없으면 생성.
    웹 로그인(CustomOAuth2UserService)과 앱 로그인(AuthController)이 함께 쓴다.

    사용자를 찾는 기준은 (socialType, socialId)다.
    이메일로 찾으면 안 된다. 구글 계정의 이메일은 바뀔 수 있고, 그러면 남남이 되어버린다.
    */
    public User findOrCreateSocialUser(SocialType socialType, String socialId, String email, String name) {

        SocialUser socialUser = socialUserRepository
                .findBySocialTypeAndSocialId(socialType, socialId)
                .orElse(null);

        if (socialUser == null) {
            try {
                //user / social_user / user_profile 세 행을 한 트랜잭션으로 만든다.
                //중간에 실패하면 user만 남는 유령 행이 생기므로 반드시 묶어야 한다.
                return self.createSocialUser(socialType, socialId, email, name);

            } catch (DataIntegrityViolationException e) {
                //동시에 들어온 다른 로그인이 먼저 저장한 경우.
                //(social_type, social_id) unique 제약이 두 번째 insert를 막아준 것이므로 다시 읽어서 이어간다.
                socialUser = socialUserRepository
                        .findBySocialTypeAndSocialId(socialType, socialId)
                        //unique 제약 위반이 아닌 다른 원인이면 숨기지 않는다.
                        .orElseThrow(() -> e);
            }
        }

        //기존 사용자는 매 로그인마다 최신 프로필로 갱신한다.
        return self.syncSocialProfile(socialUser.getUserId(), email, name);
    }

    //세 테이블을 동시에 만든다. user_id는 셋 다 같은 값이다(@MapsId가 user에서 복사해 간다).
    @Transactional
    public User createSocialUser(SocialType socialType, String socialId, String email, String name) {

        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setLoginType(LoginType.SOCIAL);
        //가입 시점에는 아직 무슨 역할인지 모르므로 기본값을 준다. 나중에 사용자가 고르게 하면 된다.
        user.setRole(Role.PASSENGER);
        //닉네임은 무작위로 만들어준다. 구글 계정 이름은 대개 실명이라 그대로 쓰면
        //다른 사용자에게 실명이 노출된다. 실명은 user_profile.name에만 둔다.
        user.setNickname(NicknameGenerator.generate());
        //IDENTITY 전략이라 이 save 시점에 insert가 나가고 user_id가 정해진다.
        //아래 두 엔티티가 그 값을 그대로 쓰므로 순서를 바꾸면 안 된다.
        userRepository.save(user);

        SocialUser socialUser = new SocialUser();
        socialUser.setUser(user); //userId는 @MapsId가 채운다
        socialUser.setSocialType(socialType);
        socialUser.setSocialId(socialId);
        socialUserRepository.save(socialUser);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setName(name);
        profile.setEmail(email);
        userProfileRepository.save(profile);

        return user;
    }

    //기존 사용자의 프로필을 소셜 계정 최신 값으로 맞춘다.
    @Transactional
    public User syncSocialProfile(Long userId, String email, String name) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("social_user는 있는데 user가 없다: " + userId));

        //닉네임이 비어 있을 때만 채운다.
        //사용자가 마이페이지에서 바꾼 닉네임을 로그인할 때마다 되돌려버리면 안 되기 때문이다.
        //(이 구조로 바꾸기 전에 만들어진 사용자가 여기로 들어온다)
        if (user.getNickname() == null) {
            user.setNickname(NicknameGenerator.generate());
        }

        //user_profile이 없는 경우(이 구조로 바꾸기 전에 만들어진 사용자)도 여기서 만들어준다.
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> {
                    UserProfile created = new UserProfile();
                    created.setUser(user);
                    return created;
                });

        //name/email은 소셜 계정이 주인이므로 매번 덮어쓴다. 사용자가 고칠 수 있는 값이 아니다.
        profile.setName(name);
        profile.setEmail(email);
        userProfileRepository.save(profile);

        return userRepository.save(user);
    }

    /* ---------------- 로컬(아이디/비밀번호) 로그인 ---------------- */

    /*
    로컬 회원가입의 입구.

    소셜 로그인의 findOrCreateSocialUser와 모양이 비슷하지만 성격이 정반대다.
    소셜은 "없으면 만든다"(가입과 로그인이 한 동작)이고,
    로컬은 "이미 있으면 실패한다"(가입과 로그인이 별개의 API)다.
    구글은 이미 본인 확인을 마친 계정을 넘겨주지만, 로컬은 아이디를 사용자가 직접 고르기 때문이다.

    이 메서드에 @Transactional을 붙이지 않은 이유:
    아래 catch에서 "이미 있는 아이디인가"를 다시 조회해야 하는데,
    제약 위반이 난 트랜잭션 안에서는 더 이상 쿼리를 보낼 수 없다(rollback-only 상태).
    그래서 저장은 트랜잭션 안(self.createLocalUser), 판단은 트랜잭션 밖(여기)에서 한다.
    */
    public User  signupLocal(SignupRequest request) {

        String loginId = request.loginId().trim();

        //먼저 조회해서 걸러준다. 사용자에게 "이미 쓰는 아이디"라고 알려주기 위한 것이지
        //중복을 막는 장치는 아니다(조회와 저장 사이에 남이 끼어들 수 있다). 그건 unique 제약이 한다.
        if (localUserRepository.existsByLoginId(loginId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "loginId already exists");
        }

        try {
            //user / local_user / user_profile 세 행을 한 트랜잭션으로 만든다.
            //중간에 실패하면 user만 남는 유령 행이 생기므로 반드시 묶어야 한다.
            return self.createLocalUser(loginId, request);

        } catch (DataIntegrityViolationException e) {
            //동시에 들어온 다른 회원가입이 먼저 저장한 경우.
            //login_id unique 제약이 두 번째 insert를 막아준 것이므로 중복으로 안내한다.
            if (localUserRepository.existsByLoginId(loginId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "loginId already exists");
            }
            //unique 제약 위반이 아닌 다른 원인이면 숨기지 않는다.
            throw e;
        }
    }

    //세 테이블을 동시에 만든다. user_id는 셋 다 같은 값이다(@MapsId가 user에서 복사해 간다).
    @Transactional
    public User createLocalUser(String loginId, SignupRequest request) {

        User user = new User();
        user.setPublicId(UUID.randomUUID());
        user.setLoginType(LoginType.LOCAL);
        //가입 시점에는 아직 무슨 역할인지 모르므로 기본값을 준다. 나중에 사용자가 고르게 하면 된다.
        user.setRole(Role.PASSENGER);
        //닉네임은 무작위로 만들어준다. 가입할 때 받은 이름은 실명이라 그대로 쓰면
        //다른 사용자에게 실명이 노출된다. 실명은 아래 user_profile.name에만 둔다.
        user.setNickname(NicknameGenerator.generate());
        //IDENTITY 전략이라 이 save 시점에 insert가 나가고 user_id가 정해진다.
        //아래 두 엔티티가 그 값을 그대로 쓰므로 순서를 바꾸면 안 된다.
        userRepository.save(user);

        LocalUser localUser = new LocalUser();
        localUser.setUser(user); //userId는 @MapsId가 채운다
        localUser.setLoginId(loginId);
        //평문은 어디에도 남기지 않는다. 로그로도 찍지 말 것.
        localUser.setPassword(passwordEncoder.encode(request.password()));
        localUserRepository.save(localUser);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setName(request.name().trim());
        profile.setEmail(request.email().trim());
        profile.setBirthDate(request.birthDate());
        //전화번호는 선택 항목이라 없을 수 있다. 그래서 바로 replace를 부르면 NPE가 난다.
        profile.setPhoneNumber(normalizePhoneNumber(request.phoneNumber()));
        //성별을 안 보냈으면 OTHER로 채운다.
        //null로 두면 이 값을 꺼내 쓰는 곳마다 null 검사를 해야 하고, 한 곳만 빠져도 NPE가 된다.
        profile.setGender(request.gender() == null ? Gender.OTHER : request.gender());
        userProfileRepository.save(profile);

        return user;
    }

    /*
    전화번호를 저장할 모양으로 다듬는다.

    빈 문자열은 "안 보냄"과 같은 뜻으로 보고 null로 바꾼다.
    ""가 그대로 저장되면 "번호가 없다"가 null과 "" 두 가지 모양으로 쌓여서,
    조회하는 쪽이 두 경우를 다 확인해야 한다.

    하이픈을 떼는 것은 예전과 같은 이유다.
    "010-1234-5678"과 "01012345678"이 서로 다른 값으로 쌓이면
    나중에 중복 확인이나 조회가 전부 어긋난다.
    (요청 형식은 010-XXXX-XXXX로 고정이지만, 저장 형식까지 같을 필요는 없다)
    */
    private static String normalizePhoneNumber(String phoneNumber) {

        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }

        return phoneNumber.replace("-", "");
    }

    /*
    로컬 로그인. 아이디로 찾고 비밀번호를 대조한다.

    아이디가 없는 경우와 비밀번호가 틀린 경우를 같은 메시지로 응답하는 것은 일부러다.
    "그 아이디는 없습니다"라고 알려주면 공격자가 가입된 아이디 목록을 만들 수 있다.
    */
    public User authenticateLocal(String loginId, String rawPassword) {

        LocalUser localUser = localUserRepository.findByLoginId(loginId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid loginId or password"));

        //저장된 값은 해시라 평문끼리 비교하듯 equals로 맞춰볼 수 없다.
        //matches가 저장된 해시에서 salt를 꺼내 같은 조건으로 다시 해시해 비교해준다.
        if (!passwordEncoder.matches(rawPassword, localUser.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid loginId or password");
        }

        return userRepository.findById(localUser.getUserId())
                .orElseThrow(() -> new IllegalStateException("local_user는 있는데 user가 없다: " + localUser.getUserId()));
    }

    /* ---------------- 마이페이지 ---------------- */

    //마이페이지 수정. null인 항목은 "바꾸지 않음"으로 본다(PATCH 방식).
    //덕분에 프론트가 바뀐 항목만 보내도 되고, 안 보낸 항목이 지워지는 사고가 없다.
    public User updateProfile(User user, String nickname) {

        if (nickname != null) {
            //앞뒤 공백은 사용자가 의도한 값이 아니므로 잘라낸다.
            String trimmed = nickname.trim();

            if (trimmed.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nickname must not be blank");
            }
            if (trimmed.length() > 20) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nickname must be 20 characters or less");
            }

            user.setNickname(trimmed);
        }

        return userRepository.save(user);
    }
}
