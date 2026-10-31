package team.codingforest.moyeota.payment.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.dto.BillingKeyInfo;
import team.codingforest.moyeota.payment.dto.CardDetail;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import team.codingforest.moyeota.payment.config.PortOneApiConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PortOneClientTest {
    private static final String BASE = "https://api.portone.io";

    private MockRestServiceServer server;
    private PortOneClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = new PortOneApiConfig().portOneRestClient("test-secret", BASE).mutate();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PortOneClient(builder.build());
    }

    @AfterEach
    void 모든_기대_요청을_검증한다() { server.verify(); }

    @Test
    void 빌링키_결제_요청과_중첩_응답을_변환한다() {
        server.expect(requestTo(BASE + "/payments/123/billing-key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json(
                        """
                        {"billingKey":"bk-1","orderName":"모여타 사전 결제",
                         "amount":{"total":12000},"currency":"KRW"}
                        """))
                .andRespond(withSuccess(
                        """
                        {"payment":{"pgTxId":"pg-123","paidAt":"2026-10-29T15:00:00+09:00"}}
                        """, MediaType.APPLICATION_JSON));
        var response = client.payWithBillingKey("123", "bk-1", 12000L);
        assertThat(response.payment().pgTxId()).isEqualTo("pg-123");
        assertThat(response.payment().paidAt()).isEqualTo("2026-10-29T15:00:00+09:00");
    }

    @Test
    void 발급된_카드_빌링키는_상태_명의_카드_정보로_풀린다() {
        server.expect(requestTo(BASE + "/billing-keys/bk-1")).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "PortOne test-secret"))
                .andRespond(withSuccess("""
                        {"status":"ISSUED","billingKey":"bk-1","customer":{"id":"user-public-id","name":{"full":"홍길동"}},
                         "methods":[{"type":"BillingKeyPaymentMethodCard",
                                     "card":{"publisher":"SHINHAN","issuer":"SHINHAN","brand":"VISA","type":"CREDIT","name":"신한카드","number":"1234-56**-****-7890","bin":"123456"}}],
                         "issuedAt":"2026-10-14T00:00:00Z"}
                        """, MediaType.APPLICATION_JSON));

        BillingKeyInfo info = client.inspectBillingKey("bk-1");

        assertThat(info.issued()).isTrue();
        assertThat(info.customerId()).isEqualTo("user-public-id");
        assertThat(info.card()).isEqualTo(new CardDetail("신한카드", "****-****-****-7890", "VISA"));
    }

    @Test
    void 삭제된_빌링키는_발급_상태가_아니다() {
        server.expect(requestTo(BASE + "/billing-keys/bk-deleted"))
                .andRespond(withSuccess("""
                        {"status":"DELETED","customer":{"id":"user-public-id"},
                         "methods":[{"type":"BillingKeyPaymentMethodCard","card":{"name":"신한카드","number":"1234-56**-****-7890","brand":"VISA"}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.inspectBillingKey("bk-deleted").issued()).isFalse();
    }

    @Test
    void 카드가_아닌_수단이면_카드_정보가_없다() {
        server.expect(requestTo(BASE + "/billing-keys/bk-easy"))
                .andRespond(withSuccess("""
                        {"status":"ISSUED","customer":{"id":"user-public-id"},
                         "methods":[{"type":"BillingKeyPaymentMethodEasyPay","provider":"KAKAOPAY"}]}
                        """, MediaType.APPLICATION_JSON));

        BillingKeyInfo info = client.inspectBillingKey("bk-easy");

        assertThat(info.issued()).isTrue();
        assertThat(info.isCard()).isFalse();
    }

    @Test
    void 결제사에_없는_빌링키는_INVALID_BILLING_KEY() {
        server.expect(requestTo(BASE + "/billing-keys/bk-none"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"BILLING_KEY_NOT_FOUND\",\"message\":\"not found\"}"));

        assertThatThrownBy(() -> client.inspectBillingKey("bk-none"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_BILLING_KEY);
    }

    @Test
    void 결제사_장애는_규격_안의_GATEWAY_ERROR로_변환된다() {
        // 정체불명 500이 아니라 502 로 나가야 앱이 "잠시 후 다시 시도" 안내를 띄울 수 있다
        server.expect(requestTo(BASE + "/billing-keys/bk-1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.inspectBillingKey("bk-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.GATEWAY_ERROR);
    }

    @Test
    void 이미_지워진_빌링키_삭제는_성공으로_본다() {
        server.expect(requestTo(BASE + "/billing-keys/bk-gone")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatCode(() -> client.deleteBillingKey("bk-gone")).doesNotThrowAnyException();
    }
    @Test
    void 인증_실패를_우리_사용자의_인증오류로_노출하지_않는다() {
        server.expect(requestTo(BASE + "/billing-keys/bk-1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> client.inspectBillingKey("bk-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.GATEWAY_ERROR);
    }

    @Test
    void 원문_카드번호는_마지막_네자리만_남긴다() {
        server.expect(requestTo(BASE + "/billing-keys/bk-1")).andRespond(withSuccess("""
                {"status":"ISSUED","customer":{"id":"customer"},"methods":[
                {"type":"BillingKeyPaymentMethodCard","card":{"number":"1234567890123456"}}]}
                """, MediaType.APPLICATION_JSON));
        assertThat(client.inspectBillingKey("bk-1").card().number()).isEqualTo("************3456");
    }

    @Test
    void 이미_삭제된_키의_409는_재시도_성공으로_본다() {
        server.expect(requestTo(BASE + "/billing-keys/bk-old")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"type\":\"BILLING_KEY_ALREADY_DELETED\"}"));
        assertThatCode(() -> client.deleteBillingKey("bk-old")).doesNotThrowAnyException();
    }

    @Test
    void 결제예약이_있는_409는_삭제성공으로_간주하지_않는다() {
        server.expect(requestTo(BASE + "/billing-keys/bk-old")).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON).body("{\"type\":\"PAYMENT_SCHEDULE_ALREADY_EXISTS\"}"));
        assertThatThrownBy(() -> client.deleteBillingKey("bk-old")).isInstanceOf(BusinessException.class);
    }

}
