package team.codingforest.moyeota.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.application.dto.AuthenticatedUser;
import team.codingforest.moyeota.user.application.dto.RefreshTokenResponse;
import team.codingforest.moyeota.user.application.dto.UserLoginCommand;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final LocalUserService localUserService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public RefreshTokenResponse login(UserLoginCommand command) {
        return issue(localUserService.authenticate(command));
    }

    @Transactional
    public RefreshTokenResponse reissue(String refreshToken) {
        return refreshTokenService.reissue(refreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.logout(refreshToken);
    }

    private RefreshTokenResponse issue(AuthenticatedUser user) {
        return refreshTokenService.issue(user.userId(), user.publicId());
    }
}