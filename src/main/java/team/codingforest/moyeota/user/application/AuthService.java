package team.codingforest.moyeota.user.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import team.codingforest.moyeota.user.application.dto.AuthenticatedUser;
import team.codingforest.moyeota.user.application.dto.TokenResponse;
import team.codingforest.moyeota.user.application.dto.UserLoginCommand;
import team.codingforest.moyeota.user.domain.Users;
import team.codingforest.moyeota.user.domain.exception.UserErrorCode;
import team.codingforest.moyeota.user.domain.exception.UserException;
import team.codingforest.moyeota.user.infrastructure.JwtProvider;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final LocalUserService localUserService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProvider jwtProvider;
    private final Users users;

    @Transactional
    public TokenResponse login(UserLoginCommand command) {
        return issue(localUserService.authenticate(command));
    }

    @Transactional
    public TokenResponse reissue(String refreshToken) {
        return refreshTokenService.reissue(refreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.logout(refreshToken);
    }

    /** Security 필터가 매 요청 호출. access 토큰엔 publicId 만 있어서 DB 로 내부 userId 를 찾는다 */
    @Transactional(readOnly = true)
    public AuthenticatedPrincipal authenticate(String accessToken) {
        UUID publicId = jwtProvider.parseAccess(accessToken);

        return users.findByPublicId(publicId)
                .map(user -> new AuthenticatedPrincipal(user.getId(), user.getPublicId()))
                .orElseThrow(() -> new UserException(UserErrorCode.TOKEN_INVALID));
    }

    private TokenResponse issue(AuthenticatedUser user) {
        return refreshTokenService.issue(user.userId(), user.publicId());
    }
}