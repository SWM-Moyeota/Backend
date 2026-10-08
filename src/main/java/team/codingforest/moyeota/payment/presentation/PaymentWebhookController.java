package team.codingforest.moyeota.payment.presentation;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import team.codingforest.moyeota.payment.application.PaymentWebhookService;

/**
 *  포트원 웹훅 수신. 인증은 토큰이 아니라 서명(webhook-signature)으로 - SecurityConfig 에서 permitAll.
 *  본문은 서명 검증을 위해 원문 그대로 받는다(역직렬화하면 바이트가 달라져 서명이 안 맞는다).
 */
@Hidden
@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
public class PaymentWebhookController {
    private final PaymentWebhookService service;

    @PostMapping
    public ResponseEntity<Void> receive(@RequestHeader(value = "webhook-id", required = false) String webhookId,
                                        @RequestHeader(value = "webhook-timestamp", required = false) String timestamp,
                                        @RequestHeader(value = "webhook-signature", required = false) String signature,
                                        @RequestBody String body) {
        service.handle(webhookId, timestamp, signature, body);

        return ResponseEntity.ok().build();
    }
}
