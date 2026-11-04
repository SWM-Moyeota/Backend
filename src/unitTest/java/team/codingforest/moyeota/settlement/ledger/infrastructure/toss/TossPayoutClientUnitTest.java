package team.codingforest.moyeota.settlement.ledger.infrastructure.toss;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TossPayoutClientUnitTest {
    private static final String BASE_URL = "https://api.tosspayments.com";
    private static final String SECRET_KEY = "test_sk_sample";
    private static final String SECURITY_KEY = "0123456789abcdef".repeat(4);
    private static final TossPayoutProperties PROPERTIES =
            new TossPayoutProperties(BASE_URL, SECRET_KEY, SECURITY_KEY, Duration.ofSeconds(3), Duration.ofSeconds(10));

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TossJweCipher cipher = new TossJweCipher(SECURITY_KEY, objectMapper);

    private MockRestServiceServer server;
    private TossPayoutClient client;

    record SellerStub(String id, String status) {
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = TossPayoutConfig.authorized(RestClient.builder(), PROPERTIES);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossPayoutClient(builder.build(), cipher, objectMapper);
    }

    @DisplayName("findBalance - 시크릿 키 뒤에 콜론을 붙여 Basic 인증하고 평문 응답의 entityBody 를 읽는다")
    @Test
    void findBalance() {

        /*
         * settings
         */
        String basic = Base64.getEncoder().encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo(BASE_URL + "/v2/balances"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Basic " + basic))
                .andRespond(withSuccess("""
                        {"version":"2022-11-16","traceId":"t-1","entityType":"balance",
                         "entityBody":{"availableAmount":{"currency":"KRW","value":50000}}}""", MediaType.APPLICATION_JSON));

        /*
         * test
         */
        TossBalance balance = client.findBalance();

        /*
         * verify
         */
        assertThat(balance.availableAmount().currency()).isEqualTo("KRW");
        assertThat(balance.availableAmount().value()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(balance.pendingAmount()).isNull();
        server.verify();
    }

    @DisplayName("postEncrypted - 본문을 JWE 로 보내고 보안 모드·멱등키 헤더를 붙이며 암호화된 응답을 풀어 돌려준다")
    @Test
    void postEncrypted() {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/sellers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("TossPayments-api-security-mode", "ENCRYPTION"))
                .andExpect(header("Idempotency-Key", "moyeota-seller-7"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(request -> assertThat(cipher.decrypt(((MockClientHttpRequest) request).getBodyAsString()))
                        .isEqualTo(objectMapper.readTree("{\"refSellerId\":\"driver-7\"}")))
                .andRespond(withSuccess(cipher.encrypt("""
                        {"version":"2022-11-16","traceId":"t-2","entityType":"seller",
                         "entityBody":{"id":"seller_1","status":"PARTIALLY_APPROVED"}}"""), MediaType.TEXT_PLAIN));

        /*
         * test
         */
        SellerStub seller = client.postEncrypted("/v2/sellers", Map.of("refSellerId", "driver-7"), "moyeota-seller-7", SellerStub.class);

        /*
         * verify
         */
        assertThat(seller).isEqualTo(new SellerStub("seller_1", "PARTIALLY_APPROVED"));
        server.verify();
    }

    @DisplayName("postEncrypted - 멱등키가 없으면 헤더를 붙이지 않는다")
    @Test
    void postEncrypted_noIdempotencyKey() {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/sellers"))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andRespond(withSuccess(cipher.encrypt("""
                        {"traceId":"t-3","entityBody":{"id":"seller_1","status":"APPROVAL_REQUIRED"}}"""), MediaType.TEXT_PLAIN));

        /*
         * test
         */
        client.postEncrypted("/v2/sellers", Map.of(), null, SellerStub.class);

        /*
         * verify
         */
        server.verify();
    }

    @DisplayName("에러 - 암호화된 에러 응답을 풀어 HTTP 상태·코드·traceId 를 담은 예외로 던진다")
    @Test
    void error_encrypted() {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/sellers"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_PLAIN).body(cipher.encrypt("""
                        {"traceId":"t-4","error":{"code":"INVALID_REQUEST","message":"잘못된 요청입니다."}}""")));

        /*
         * test
         */
        TossPayoutException thrown = catchThrowableOfType(TossPayoutException.class,
                () -> client.postEncrypted("/v2/sellers", Map.of(), null, SellerStub.class));

        /*
         * verify
         */
        assertThat(thrown.getHttpStatus()).isEqualTo(400);
        assertThat(thrown.getCode()).isEqualTo("INVALID_REQUEST");
        assertThat(thrown.getTraceId()).isEqualTo("t-4");
    }

    @DisplayName("에러 - 평문 에러 응답(인증 실패 등)도 같은 예외로 던진다")
    @Test
    void error_plain() {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/balances"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("""
                        {"traceId":"t-5","error":{"code":"UNAUTHORIZED_KEY","message":"인증되지 않은 키입니다."}}"""));

        /*
         * test
         */
        TossPayoutException thrown = catchThrowableOfType(TossPayoutException.class, () -> client.findBalance());

        /*
         * verify
         */
        assertThat(thrown.getHttpStatus()).isEqualTo(401);
        assertThat(thrown.getCode()).isEqualTo("UNAUTHORIZED_KEY");
    }

    @DisplayName("에러 - JSON 도 JWE 도 아닌 응답은 상태만 담아 던진다")
    @Test
    void error_unreadable() {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/balances"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body("<html>bad gateway</html>"));

        /*
         * test
         */
        TossPayoutException thrown = catchThrowableOfType(TossPayoutException.class, () -> client.findBalance());

        /*
         * verify
         */
        assertThat(thrown.getHttpStatus()).isEqualTo(502);
        assertThat(thrown.getCode()).isNull();
    }
}
