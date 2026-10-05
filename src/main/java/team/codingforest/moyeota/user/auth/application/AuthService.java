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
     *  Security 필터가 매 요청 호출. access 토큰엔 publicId 만 있어서 내부 userId 로 바꿔야 한다.
     *  여기에 @Transactional 을 붙이지 않는다 - 붙이면 캐시에서 값을 찾아도 트랜잭션이 먼저 열려 DB 커넥션을 얻고,
     *  커넥션 풀이 붐빌 때 모든 요청이 서비스 진입 전에 한 번 더 줄을 선다.
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