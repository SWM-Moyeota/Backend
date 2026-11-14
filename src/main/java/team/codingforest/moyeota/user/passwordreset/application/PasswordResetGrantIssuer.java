package team.codingforest.moyeota.user.passwordreset.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import team.codingforest.moyeota.user.passwordreset.domain.*;
import team.codingforest.moyeota.user.passwordreset.dto.PasswordResetVerifyResponse;
import team.codingforest.moyeota.user.passwordreset.infrastructure.PasswordResetSecrets;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;

@Service
@RequiredArgsConstructor
public class PasswordResetGrantIssuer {
    private final ResetAccounts accounts;
    private final ResetGrants grants;
    private final PasswordResetSecrets secrets;
    private final Clock clock;

    @Transactional
    public PasswordResetVerifyResponse issue(VerifiedResetProof proof) {
        ResetAccount account = accounts.lockEligible(proof.userId())
                .orElseThrow(this::verificationFailed);

        if (account.generation() != proof.generation()
                || !account.phoneHash().equals(proof.phoneHash())) {
            throw verificationFailed();
        }

        Instant now = clock.instant();
        String rawToken = secrets.newOpaqueValue();
        grants.save(new ResetGrant(
                UUID.randomUUID(), secrets.tokenDigest(rawToken),
                account.userId(), account.phoneHash(), account.generation(),
                now.plusSeconds(600), null, now));

        return new PasswordResetVerifyResponse(rawToken, 600);
    }

    private UserException verificationFailed() {
        return new UserException(UserErrorCode.PASSWORD_RESET_VERIFICATION_FAILED);
    }
}
