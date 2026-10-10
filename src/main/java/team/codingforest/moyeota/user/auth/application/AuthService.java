package team.codingforest.moyeota.user.auth.application;

import team.codingforest.moyeota.user.local.application.LocalUserService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import team.codingforest.moyeota.user.auth.dto.AuthenticatedUser;
import team.codingforest.moyeota.user.auth.dto.TokenResponse;
import team.codingforest.moyeota.user.auth.dto.UserLoginCommand;
import team.codingforest.moyeota.user.auth.domain.PrincipalCache;
import team.codingforest.moyeota.user.common.domain.User;
import team.codingforest.moyeota.user.common.domain.Users;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.auth.infrastructure.JwtProvider;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final LocalUserService localUserService;
    private final RefreshTokenService refreshTokenService;
    private final JwtProvider jwtProvider;
    private final Users users;
    private final PrincipalCache principalCache;

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

    /**
     *
     * @param accessToken
     * @return Redis에서 uuid값을 id값으로 캐싱해서 사용 -> uuid 값을 id 값으로 변환할때 DB 한번 더 조회하는것을 없애기 위함
     */
    public AuthenticatedPrincipal authenticate(String accessToken) {
        UUID publicId = jwtProvider.parseAccess(accessToken);   // 서명·만료 검증은 매번 한다

        Long userId = principalCache.findUserId(publicId)
                .orElseGet(() -> loadAndCache(publicId));

        return new AuthenticatedPrincipal(userId, publicId);
    }

    private Long loadAndCache(UUID publicId) {
        Long userId = users.findByPublicId(publicId)
                .map(User::getId)
                .orElseThrow(() -> new UserException(UserErrorCode.TOKEN_INVALID));   // 없는 사용자는 캐시하지 않는다

        principalCache.save(publicId, userId);
        return userId;
    }

    private TokenResponse issue(AuthenticatedUser user) {
        return refreshTokenService.issue(user.userId(), user.publicId());
    }
}