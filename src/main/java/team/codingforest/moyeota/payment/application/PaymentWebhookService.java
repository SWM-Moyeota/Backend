package team.codingforest.moyeota.payment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.payment.domain.WebhookNotice;
import team.codingforest.moyeota.payment.domain.WebhookVerifier;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {
    private final WebhookVerifier verifier;
    private final PaymentGroupService paymentGroupService;

    /** 서명이 틀리면 INVALID_WEBHOOK_SIGNATURE(401). 통과하면 결제사 원본을 다시 조회해 반영한다 */
    public void handle(String webhookId, String timestamp, String signature, String body) {
        WebhookNotice notice = verifier.verify(webhookId, timestamp, signature, body);

        if(notice.pgPaymentId() == null) {
            log.info("결제 외 웹훅 - 동기화 대상 아님 type={}", notice.type());
            return;
        }

        log.info("결제 웹훅 수신 type={}, pgPaymentId={}", notice.type(), notice.pgPaymentId());
        paymentGroupService.sync(notice.pgPaymentId());
    }
}
