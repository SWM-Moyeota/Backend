package team.codingforest.moyeota.user.verification.infrastructure;

import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import team.codingforest.moyeota.user.common.exception.UserErrorCode;
import team.codingforest.moyeota.user.common.exception.UserException;
import team.codingforest.moyeota.user.common.masking.Masking;
import team.codingforest.moyeota.user.verification.domain.SmsSender;

@Slf4j
@Component
@ConditionalOnProperty(name = "moyeota.sms.provider", havingValue = "solapi")
public class SolapiSmsSender implements SmsSender {
    private final DefaultMessageService messageService;
    private final String from;

    public SolapiSmsSender(@Value("${moyeota.sms.solapi.api-key}") String apiKey,
                           @Value("${moyeota.sms.solapi.api-secret}") String apiSecret,
                           @Value("${moyeota.sms.solapi.from}") String from) {
        Assert.hasText(apiKey, "moyeota.sms.solapi.api-key 가 비어 있습니다");
        Assert.hasText(apiSecret, "moyeota.sms.solapi.api-secret 가 비어 있습니다");
        Assert.hasText(from, "moyeota.sms.solapi.from 이 비어 있습니다");
        this.messageService = SolapiClient.INSTANCE.createInstance(apiKey, apiSecret);
        this.from = from;
    }

    @Override
    public void send(String phoneNumber, String text) {
        Message message = new Message();
        message.setFrom(from);
        message.setTo(phoneNumber);
        message.setText(text);
        try {
            messageService.send(message);
        } catch (Exception e) {
            log.warn("SOLAPI 문자 발송 실패 to={}", Masking.phone(phoneNumber), e);
            throw new UserException(UserErrorCode.SMS_SEND_FAILED);
        }
    }
}