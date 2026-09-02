package team.codingforest.moyeota.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.application.dto.*;
import team.codingforest.moyeota.user.domain.*;
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

        User user = users.save(User.from(idGenerator.generate(), LoginType.LOCAL));

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
        return new UserResponse(user.getPublicId(), user.getNickname());
    }
}
