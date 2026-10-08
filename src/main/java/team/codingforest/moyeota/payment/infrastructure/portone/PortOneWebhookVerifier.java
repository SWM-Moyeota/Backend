package team.codingforest.moyeota.payment.infrastructure.portone;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.domain.WebhookNotice;
import team.codingforest.moyeota.payment.domain.WebhookVerifier;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 *  포트원 V2 웹훅 = Standard Webhooks 규격.
 *  헤더 webhook-id / webhook-timestamp / webhook-signature("v1,{base64}" 공백 구분 복수 가능),
 *  서명 대상 = "{id}.{timestamp}.{body}" 의 HMAC-SHA256, 키 = "whsec_" 뒤 base64 를 디코딩한 바이트.
 */
@Slf4j
@Component
public class PortOneWebhookVerifier implements WebhookVerifier {
    static final Duration TOLERANCE = Duration.ofMinutes(5);
    private static final String SECRET_PREFIX = "whsec_";
    private static final String SIGNATURE_VERSION = "v1,";

    private final byte[] secret;
    private final ObjectMapper objectMapper;

    public PortOneWebhookVerifier(@Value("${portone.webhook.secret:}") String webhookSecret, ObjectMapper objectMapper) {
        this.secret = decodeSecret(webhookSecret);
        this.objectMapper = objectMapper;
    }

    @Override
    public WebhookNotice verify(String webhookId, String timestamp, String signature, String body) {
        if(secret.length == 0) {
            log.warn("portone.webhook.secret 미설정 - 웹훅 거부");
            throw new BusinessException(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }
        if(webhookId == null || timestamp == null || signature == null || body == null) {
            throw new BusinessException(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }

        ensureFresh(timestamp);

        String expected = sign(webhookId + "." + timestamp + "." + body);
        if(!matchesAny(expected, signature)) throw new BusinessException(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);

        return parse(body);
    }

    private void ensureFresh(String timestamp) {
        try {
            Instant sent = Instant.ofEpochSecond(Long.parseLong(timestamp.trim()));
            Duration skew = Duration.between(sent, Instant.now()).abs();
            if(skew.compareTo(TOLERANCE) > 0) throw new BusinessException(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
        } catch (NumberFormatException e) {
            throw new BusinessException(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 사용 불가", e);
        }
    }

    private static boolean matchesAny(String expected, String header) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);

        for(String part : header.trim().split("\\s+")) {
            if(!part.startsWith(SIGNATURE_VERSION)) continue;
            byte[] given = part.substring(SIGNATURE_VERSION.length()).getBytes(StandardCharsets.UTF_8);
            if(MessageDigest.isEqual(expectedBytes, given)) return true;   // 타이밍 공격 방지
        }

        return false;
    }

    private WebhookNotice parse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String type = root.path("type").asString(null);
            JsonNode paymentId = root.path("data").path("paymentId");

            return new WebhookNotice(type, paymentId.isMissingNode() || paymentId.isNull() ? null : paymentId.asString());
        } catch (RuntimeException e) {
            log.warn("웹훅 본문 파싱 실패", e);
            throw new BusinessException(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }
    }

    private static byte[] decodeSecret(String raw) {
        if(raw == null || raw.isBlank()) return new byte[0];

        String value = raw.startsWith(SECRET_PREFIX) ? raw.substring(SECRET_PREFIX.length()) : raw;

        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return value.getBytes(StandardCharsets.UTF_8);   // base64 가 아닌 평문 시크릿도 허용
        }
    }
}
