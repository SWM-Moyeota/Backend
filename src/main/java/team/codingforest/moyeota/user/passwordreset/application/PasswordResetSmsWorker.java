package team.codingforest.moyeota.user.passwordreset.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.user.passwordreset.domain.*;
import team.codingforest.moyeota.user.passwordreset.infrastructure.PasswordResetSecrets;
import team.codingforest.moyeota.user.verification.domain.SmsSender;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="moyeota.password-reset.worker-enabled", havingValue="true", matchIfMissing=true)
public class PasswordResetSmsWorker {
    private final ResetRequests requests;
    private final ResetAccounts accounts;
    private final PasswordResetSecrets secrets;
    private final SmsSender smsSender;

    @Scheduled(fixedDelayString="${moyeota.password-reset.poll-delay-ms:1000}")
    public void drain() {
        for (int i = 0; i < 20; i++) {
            try {
                var next = requests.claimNext();
                if (next.isEmpty()) return;
                handle(next.get());
            } catch (RuntimeException e) {
                // 예외 원문, 회원·번호·코드·토큰은 기록하지 않는다.
                log.warn("비밀번호 재설정 발송 작업 실패 type={}", e.getClass().getSimpleName());
                return;
            }
        }
    }

    public void handle(ResetRequestJob job) {
        try {
            var found = accounts.findEligible(job.loginId(), job.phoneHash());
            if (found.isEmpty()) { requests.invalidate(job.requestId()); return; }
            ResetAccount account = found.get();
            String code = secrets.newCode();
            var proof = new VerifiedResetProof(account.userId(), account.phoneHash(), account.generation());
            if (!requests.prepareCode(job.requestId(), proof, secrets.codeDigest(job.requestId(), code))) return;
            smsSender.send(account.phoneNumber(), "[모여타] 비밀번호 재설정 인증번호 [%s]를 입력해 주세요.".formatted(code));
            requests.markSent(job.requestId());
        } catch (RuntimeException e) {
            requests.invalidate(job.requestId());
            throw e;
        }
    }
}
