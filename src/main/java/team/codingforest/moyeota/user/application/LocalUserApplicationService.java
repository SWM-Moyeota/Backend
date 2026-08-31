package team.codingforest.moyeota.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.user.application.dto.UserRegisterRequest;
import team.codingforest.moyeota.user.application.dto.UserRequestCommand;
import team.codingforest.moyeota.user.application.dto.UserResponse;
import team.codingforest.moyeota.user.domain.*;
import team.codingforest.moyeota.user.domain.enums.LoginType;

@Service
@RequiredArgsConstructor
public class LocalUserApplicationService {
    private final Users users;
    private final LocalUsers localUsers;
    private final UserProfiles userProfiles;

    public UserResponse register(UserRegisterRequest request) {

        if(localUsers.existsByLoginId(request.loginId())) throw new IllegalArgumentException("이미 존재하는 아이디입니다.");

        User user = users.save(User.from(LoginType.LOCAL));

        localUsers.register(user.getId(), request.loginId(), request.password());

        UserProfile userProfile = UserProfile.of(user.getId(), request.name(), request.birthDate(), request.phoneNumber(), request.gender(), request.email());

        userProfiles.save(userProfile);

        return new UserResponse(user.getPublicId());
    }

    public boolean matches(UserRequestCommand command) {
        LocalUser localUser = localUsers.findByLoginId(command.loginId());

        return localUsers.validatePassword(command.password(), localUser.getPassword());
    }

    public boolean existsByLoginId(String loginId) {
        return localUsers.existsByLoginId(loginId);
    }
}
