package team.codingforest.moyeota.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.user.application.dto.*;
import team.codingforest.moyeota.user.domain.*;
import team.codingforest.moyeota.user.domain.enums.LoginType;

@Service
@RequiredArgsConstructor
public class LocalUserService {
    private final Users users;
    private final LocalUsers localUsers;
    private final UserProfiles userProfiles;
    private final PasswordHasher passwordHasher;

    public UserResponse register(UserRegisterCommand command) {
        if(localUsers.existsByLoginId(command.loginId())) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }

        User user = users.save(User.from(LoginType.LOCAL));

        localUsers.register(user.getId(), command.loginId(), passwordHasher.hash(command.password()));

        UserProfile userProfile = UserProfile.of(user.getId(), command.name(), command.birthDate(), command.phoneNumber(), command.gender(), command.email());

        userProfiles.save(userProfile);

        return new UserResponse(user.getPublicId());
    }

    public AuthenticatedUser authenticate(UserLoginCommand command) {
        LocalUser localUser = localUsers.findByLoginId(command.loginId());
        if (!passwordHasher.matches(command.password(), localUser.getPassword())) {
            throw new IllegalArgumentException("아이디나 비밀번호가 다릅니다.");
        }

        User user = users.findById(localUser.getUserId());

        return new AuthenticatedUser(user.getId(), user.getPublicId());
    }
}
