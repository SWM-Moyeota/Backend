package team.codingforest.moyeota.user.passwordreset.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Instant;
import team.codingforest.moyeota.user.passwordreset.domain.ResetRequestJob;
import team.codingforest.moyeota.user.passwordreset.domain.ResetRequests;
import team.codingforest.moyeota.user.passwordreset.dto.PasswordResetSendRequest;
import team.codingforest.moyeota.user.passwordreset.dto.PasswordResetSendResponse;
import team.codingforest.moyeota.user.passwordreset.infrastructure.PasswordResetSecrets;
import team.codingforest.moyeota.user.users.domain.PhoneNumber;

@Service
@RequiredArgsConstructor
public class PasswordResetSendService {
    private final PasswordResetSecrets secrets;
    private final ResetRequests requests;
    private final Clock clock;

    public PasswordResetSendResponse send(
            PasswordResetSendRequest input) {
        String phone = new PhoneNumber(input.phoneNumber()).value();
        String requestId = secrets.newOpaqueValue();
        Instant now = clock.instant();

        requests.accept(new ResetRequestJob(
                requestId,
                input.loginId(),
                secrets.phoneDigest(phone),
                now.plusSeconds(180)
        ));

        return new PasswordResetSendResponse(
                requestId, 180, 60,
                "입력한 정보가 가입 정보와 일치하면 인증번호가 발송됩니다."
        );
    }
}
