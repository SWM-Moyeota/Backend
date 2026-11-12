package team.codingforest.moyeota.user.passwordreset.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import team.codingforest.moyeota.user.passwordreset.domain.*;
import team.codingforest.moyeota.user.passwordreset.dto.PasswordResetConfirmRequest;
import team.codingforest.moyeota.user.passwordreset.infrastructure.PasswordResetSecrets;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.local.domain.PasswordHasher;
import team.codingforest.moyeota.user.local.domain.PasswordPolicy;
import team.codingforest.moyeota.user.auth.domain.RefreshTokens;

@Service
@RequiredArgsConstructor
public class PasswordResetConfirmService {
    private final ResetAccounts accounts;
    private final ResetGrants grants;
    private final PasswordResetSecrets secrets;
    private final PasswordHasher passwordHasher;
    private final RefreshTokens refreshTokens;
    private final Clock clock;

    @Transactional
    public void confirm(PasswordResetConfirmRequest input) {
        // 실패해도 허가증은 사용하지 않는다.
        PasswordPolicy.validateConfirmation(
                input.newPassword(), input.newPasswordConfirm());

        String tokenHash = secrets.tokenDigest(input.resetToken());
        Long userId = grants.findOwnerId(tokenHash)
                .orElseThrow(this::invalidToken);

        // 모든 경로에서 회원 먼저, 허가증 다음 순서로 잠근다.
        ResetAccount account = accounts.lockEligible(userId)
                .orElseThrow(this::invalidToken);
        ResetGrant grant = grants.lockByTokenHash(tokenHash)
                .orElseThrow(this::invalidToken);

        // 잠금 대기 중 만료될 수 있으므로 잠금 이후 시간을 사용한다.
        Instant now = clock.instant();
        if (!grant.usableAt(now)
                || !grant.userId().equals(account.userId())
                || grant.generation() != account.generation()
                || !grant.phoneHash().equals(account.phoneHash())) {
            throw invalidToken();
        }

        if (passwordHasher.matches(input.newPassword(), account.passwordHash())) {
            throw new UserException(UserErrorCode.SAME_PASSWORD);
        }

        String newHash = passwordHasher.hash(input.newPassword());
        // 해시 계산 중 만료된 경우도 완료시키지 않는다.
        now = clock.instant();
        if (!grant.usableAt(now)) {
            throw invalidToken();
        }

        accounts.changePassword(userId, newHash, now); // 세대도 +1
        grants.consumeAll(userId, now);               // 현재·다른 허가증 무효화
        refreshTokens.cancelAllByUserId(userId, now);
    }

    private UserException invalidToken() {
        return new UserException(UserErrorCode.PASSWORD_RESET_TOKEN_INVALID);
    }
}
