package team.codingforest.moyeota.user.passwordreset.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.user.passwordreset.domain.ResetRequests;
import team.codingforest.moyeota.user.passwordreset.domain.VerifiedResetProof;
import team.codingforest.moyeota.user.passwordreset.dto.PasswordResetVerifyRequest;
import team.codingforest.moyeota.user.passwordreset.dto.PasswordResetVerifyResponse;
import team.codingforest.moyeota.user.passwordreset.infrastructure.PasswordResetSecrets;

@Service
@RequiredArgsConstructor
public class PasswordResetVerifyService {
    private final ResetRequests requests;
    private final PasswordResetSecrets secrets;
    private final PasswordResetGrantIssuer issuer;

    public PasswordResetVerifyResponse verify(PasswordResetVerifyRequest input) {
        VerifiedResetProof proof = requests.verifyAndConsume(
                input.requestId(),
                secrets.codeDigest(input.requestId(), input.code()));
        return issuer.issue(proof);
    }
}
