package team.codingforest.moyeota.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.application.dto.AuthenticatedUser;
import team.codingforest.moyeota.user.application.dto.UserLoginCommand;
import team.codingforest.moyeota.user.application.dto.UserRegisterCommand;
import team.codingforest.moyeota.user.application.dto.UserResponse;
import team.codingforest.moyeota.user.domain.IdGenerator;
import team.codingforest.moyeota.user.domain.LocalUser;
import team.codingforest.moyeota.user.domain.LocalUsers;
import team.codingforest.moyeota.user.domain.Nickname;
import team.codingforest.moyeota.user.domain.PasswordHasher;
import team.codingforest.moyeota.user.domain.PhoneNumber;
import team.codingforest.moyeota.user.domain.User;
import team.codingforest.moyeota.user.domain.UserProfile;
import team.codingforest.moyeota.user.domain.UserProfiles;
import team.codingforest.moyeota.user.domain.Users;
import team.codingforest.moyeota.user.domain.enums.LoginType;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;

@Service
@RequiredArgsConstructor
public class LocalUserService {
    private final Users users;
    private final LocalUsers localUsers;
    private final UserProfiles userProfiles;
    private final PasswordHasher passwordHasher;
    private final IdGenerator idGenerator;

    @Transactional // users / local_user / profile 3개 테이블 원자 저장
    public UserResponse register(UserRegisterCommand command) {
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

        return new UserResponse(user.getPublicId(), user.getNickname());
    }

    public AuthenticatedUser authenticate(UserLoginCommand command) {
        LocalUser localUser = localUsers.findByLoginId(command.loginId());
        if (!passwordHasher.matches(command.password(), localUser.getPassword())) {
            throw new UserException(UserErrorCode.LOGIN_FAILED);
        }

        User user = users.findById(localUser.getUserId());

        return new AuthenticatedUser(user.getId(), user.getPublicId());
    }

    public UserResponse getProfile(Long userId) {
        User user = users.findById(userId);
        // 닉네임 미설정(가입 직후 기본 상태)이면 실명으로 폴백한다
        String name = user.getNickname() != null ? user.getNickname()
                : userProfiles.findByUserId(userId).map(UserProfile::getName).orElse(null);
        return new UserResponse(user.getPublicId(), name);
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
