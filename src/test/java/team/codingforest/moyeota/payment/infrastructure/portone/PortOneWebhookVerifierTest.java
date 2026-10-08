package team.codingforest.moyeota.payment.infrastructure.portone;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.domain.WebhookNotice;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortOneWebhookVerifierTest {
    private static final byte[] 키 = "test-webhook-secret-bytes-32-long!!".getBytes(StandardCharsets.UTF_8);
    private static final String 시크릿 = "whsec_" + Base64.getEncoder().encodeToString(키);
    private static final String 결제통지 = """
            {"type":"Transaction.Paid","timestamp":"2026-10-09T10:00:00Z","data":{"storeId":"store-1","paymentId":"moyeota-abc","transactionId":"tx-1"}}""";

    private final PortOneWebhookVerifier verifier = new PortOneWebhookVerifier(시크릿, new ObjectMapper());

    private static String 서명(String id, String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(키, "HmacSHA256"));
        return "v1," + Base64.getEncoder().encodeToString(mac.doFinal((id + "." + timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }

    private static String 지금() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    @Test
    void 올바른_서명이면_결제_식별자를_꺼낸다() throws Exception {
        String ts = 지금();

        WebhookNotice notice = verifier.verify("msg-1", ts, 서명("msg-1", ts, 결제통지), 결제통지);

        assertThat(notice.type()).isEqualTo("Transaction.Paid");
        assertThat(notice.pgPaymentId()).isEqualTo("moyeota-abc");
    }

    @Test
    void 여러_서명_중_하나만_맞아도_통과한다() throws Exception {
        // 시크릿 교체 기간엔 구·신 서명이 같이 온다
        String ts = 지금();

        WebhookNotice notice = verifier.verify("msg-1", ts, "v1,b3RoZXI= " + 서명("msg-1", ts, 결제통지), 결제통지);

        assertThat(notice.pgPaymentId()).isEqualTo("moyeota-abc");
    }

    @Test
    void 본문이_바뀌면_거부한다() throws Exception {
        String ts = 지금();
        String tampered = 결제통지.replace("moyeota-abc", "moyeota-xyz");

        assertThatThrownBy(() -> verifier.verify("msg-1", ts, 서명("msg-1", ts, 결제통지), tampered))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
    }

    @Test
    void 오래된_타임스탬프는_거부한다() throws Exception {
        // 가로챈 웹훅 재전송(replay) 차단
        String old = String.valueOf(Instant.now().minus(PortOneWebhookVerifier.TOLERANCE).minusSeconds(60).getEpochSecond());

        assertThatThrownBy(() -> verifier.verify("msg-1", old, 서명("msg-1", old, 결제통지), 결제통지))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
    }

    @Test
    void 헤더가_빠지면_거부한다() {
        assertThatThrownBy(() -> verifier.verify(null, 지금(), null, 결제통지))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
    }

    @Test
    void 시크릿이_설정되지_않았으면_전부_거부한다() throws Exception {
        // 시크릿 없이 열어두면 누구나 "결제됨" 통지를 보낼 수 있다 - 실제 반영은 결제사 재조회로 막히지만 입구부터 닫는다
        PortOneWebhookVerifier unconfigured = new PortOneWebhookVerifier("", new ObjectMapper());
        String ts = 지금();

        assertThatThrownBy(() -> unconfigured.verify("msg-1", ts, 서명("msg-1", ts, 결제통지), 결제통지))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_WEBHOOK_SIGNATURE);
    }

    @Test
    void 결제가_아닌_통지는_식별자_없이_돌려준다() throws Exception {
        String body = """
                {"type":"BillingKey.Issued","timestamp":"2026-10-09T10:00:00Z","data":{"storeId":"store-1","billingKey":"bk-1"}}""";
        String ts = 지금();

        WebhookNotice notice = verifier.verify("msg-2", ts, 서명("msg-2", ts, body), body);

        assertThat(notice.type()).isEqualTo("BillingKey.Issued");
        assertThat(notice.pgPaymentId()).isNull();
    }
}
