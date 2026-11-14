package team.codingforest.moyeota.user.verification.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.user.common.masking.Masking;
import team.codingforest.moyeota.user.verification.domain.SmsSender;

@Slf4j
@Component
@ConditionalOnProperty(name = "moyeota.sms.provider", havingValue = "log", matchIfMissing = true)
public class LoggingSmsSender implements SmsSender {

    @Override
    public void send(String phoneNumber, String text) {
        log.info("[SMS 미발송] to={} (인증번호 본문 생략)", Masking.phone(phoneNumber));
    }
}
