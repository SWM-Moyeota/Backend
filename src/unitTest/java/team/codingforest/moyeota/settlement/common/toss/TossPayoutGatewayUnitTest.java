package team.codingforest.moyeota.settlement.common.toss;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutOrder;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TossPayoutGatewayUnitTest {
    private static final String BASE_URL = "https://api.tosspayments.com";
    private static final String SECURITY_KEY = "0123456789abcdef".repeat(4);
    private static final TossPayoutProperties PROPERTIES =
            new TossPayoutProperties(BASE_URL, "test_sk_sample", SECURITY_KEY, Duration.ofSeconds(3), Duration.ofSeconds(10));
    private static final PayoutOrder ORDER = new PayoutOrder(17L, "moyeota-payout-17", 45000, "seller_abc");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TossJweCipher cipher = new TossJweCipher(SECURITY_KEY, objectMapper);

    private MockRestServiceServer server;
    private TossPayoutGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = TossPayoutConfig.authorized(RestClient.builder(), PROPERTIES);
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new TossPayoutGateway(new TossPayoutClient(builder.build(), cipher, objectMapper));
    }

    @DisplayName("request - 지급 키를 refPayoutId 와 멱등키로 같이 쓰고 셀러에게 바로지급하는 한 건짜리 배열을 암호화해 보낸다")
    @Test
    void request_body() {

        /*
         * settings
         */
        AtomicReference<JsonNode> sent = new AtomicReference<>();
        server.expect(requestTo(BASE_URL + "/v2/payouts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("TossPayments-api-security-mode", "ENCRYPTION"))
                .andExpect(header("Idempotency-Key", "moyeota-payout-17"))
                .andExpect(request -> sent.set(cipher.decrypt(((MockClientHttpRequest) request).getBodyAsString())))
                .andRespond(withSuccess(cipher.encrypt(payoutList("FPA_1", "REQUESTED")), MediaType.TEXT_PLAIN));

        /*
         * test
         */
        PayoutResult result = gateway.request(ORDER);

        /*
         * verify
         */
        assertThat(result).isEqualTo(new PayoutResult.Accepted("FPA_1"));
        assertThat(sent.get()).isEqualTo(objectMapper.readTree("""
                [{"refPayoutId":"moyeota-payout-17","destination":"seller_abc","scheduleType":"EXPRESS",
                  "amount":{"currency":"KRW","value":45000},"transactionDescription":"모여타정산"}]"""));
        server.verify();
    }

    @DisplayName("request - 토스 지급 상태를 접수, 성공, 실패, 모름으로 바꾼다")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "REQUESTED, Accepted",
            "IN_PROGRESS, Accepted",
            "COMPLETED, Succeeded",
            "FAILED, Failed",
            "REJECTED, Failed",
            "CANCELED, Failed",
            "DELETED, Failed",
            "ON_HOLD, Unknown"
    })
    void request_status(String status, String resultType) {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/payouts"))
                .andRespond(withSuccess(cipher.encrypt(payoutList("FPA_1", status)), MediaType.TEXT_PLAIN));

        /*
         * test
         */
        PayoutResult result = gateway.request(ORDER);

        /*
         * verify
         */
        assertThat(result.getClass().getSimpleName()).isEqualTo(resultType);
    }

    @DisplayName("request - 실패한 지급은 토스 에러 코드와 메시지를 사유로 남긴다")
    @Test
    void request_failedWithError() {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/payouts"))
                .andRespond(withSuccess(cipher.encrypt("""
                        {"entityBody":{"items":[{"id":"FPA_1","refPayoutId":"moyeota-payout-17","status":"FAILED",
                         "error":{"code":"INVALID_ACCOUNT","message":"계좌 정보가 올바르지 않습니다."}}]}}"""), MediaType.TEXT_PLAIN));

        /*
         * test
         */
        PayoutResult result = gateway.request(ORDER);

        /*
         * verify
         */
        assertThat(result).isEqualTo(new PayoutResult.Failed("INVALID_ACCOUNT: 계좌 정보가 올바르지 않습니다."));
    }

    @DisplayName("request - 응답에 지급이 없거나 id 가 없으면 결과를 모른다")
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{\"entityBody\":{\"items\":[]}}",
            "{\"entityBody\":{}}",
            "{\"entityBody\":{\"items\":[{\"status\":\"REQUESTED\"}]}}"
    })
    void request_emptyOrInvalidItems(String body) {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/payouts"))
                .andRespond(withSuccess(cipher.encrypt(body), MediaType.TEXT_PLAIN));

        /*
         * test
         */
        PayoutResult result = gateway.request(ORDER);

        /*
         * verify
         */
        assertThat(result).isInstanceOf(PayoutResult.Unknown.class);
    }

    @DisplayName("request - 토스에 지급이 안 생긴 게 확실한 4xx 만 실패로, 생겼을 수 있거나 결과를 모르는 에러는 모름으로 둔다")
    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            "400, INVALID_REQUEST, Failed",
            "400, EXCEEDED_PAYOUT_BALANCE_AMOUNT, Failed",
            "400, INVALID_SELLER_INFO, Failed",
            "401, UNAUTHORIZED_KEY, Failed",
            "403, FORBIDDEN_SELLER_PAYOUT, Failed",
            "403, FORBIDDEN_REQUEST, Failed",
            "404, NOT_FOUND_SELLER, Failed",
            "400, DUPLICATE_PAYOUT_REQUEST, Unknown",
            "400, PREVIOUS_REQUEST_IN_PROGRESS, Unknown",
            "429, TOO_MANY_REQUESTS, Unknown",
            "500, COMMON_ERROR, Unknown",
            "503, COMMON_ERROR, Unknown"
    })
    void request_error(int status, String code, String resultType) {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/payouts"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON).body("""
                        {"traceId":"t-1","error":{"code":"%s","message":"실패"}}""".formatted(code)));

        /*
         * test
         */
        PayoutResult result = gateway.request(ORDER);

        /*
         * verify
         */
        assertThat(result.getClass().getSimpleName()).isEqualTo(resultType);
        assertThat(result.toString()).contains(code).contains("traceId=t-1");
    }

    @DisplayName("request - 4xx 인데 본문을 읽을 수 없어 코드가 없으면 결과를 모른다")
    @Test
    void request_unreadable4xx() {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/payouts"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_HTML).body("<html>Bad Request</html>"));

        /*
         * test
         */
        PayoutResult result = gateway.request(ORDER);

        /*
         * verify
         */
        assertThat(result).isInstanceOf(PayoutResult.Unknown.class);
    }

    @DisplayName("request - 응답 없이 시간이 초과되면 결과를 모른다")
    @Test
    void request_timeout() {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/payouts"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        /*
         * test
         */
        PayoutResult result = gateway.request(ORDER);

        /*
         * verify
         */
        assertThat(result).isEqualTo(new PayoutResult.Unknown("토스 응답 없음: ResourceAccessException"));
    }

    @DisplayName("find - 토스 지급 id 로 조회해 상태를 바꾼다")
    @Test
    void find() {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/payouts/FPA_1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"entityBody":{"id":"FPA_1","refPayoutId":"moyeota-payout-17","status":"COMPLETED"}}""", MediaType.APPLICATION_JSON));

        /*
         * test
         */
        PayoutResult result = gateway.find("FPA_1");

        /*
         * verify
         */
        assertThat(result).isEqualTo(new PayoutResult.Succeeded("FPA_1"));
        server.verify();
    }

    @DisplayName("find - 아직 처리 중이면 접수 결과를 준다")
    @Test
    void find_inProgress() {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/payouts/FPA_1"))
                .andRespond(withSuccess("""
                        {"entityBody":{"id":"FPA_1","status":"IN_PROGRESS"}}""", MediaType.APPLICATION_JSON));

        /*
         * test
         */
        PayoutResult result = gateway.find("FPA_1");

        /*
         * verify
         */
        assertThat(result).isEqualTo(new PayoutResult.Accepted("FPA_1"));
    }

    @DisplayName("find - 조회가 에러거나 응답이 없으면 실패로 만들지 않고 결과를 모른다")
    @Test
    void find_error() {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/payouts/FPA_1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("""
                        {"traceId":"t-9","error":{"code":"NOT_FOUND","message":"존재하지 않는 정보 입니다."}}"""));
        server.expect(requestTo(BASE_URL + "/v2/payouts/FPA_2"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        /*
         * test
         */
        PayoutResult notFound = gateway.find("FPA_1");
        PayoutResult timeout = gateway.find("FPA_2");

        /*
         * verify
         */
        assertThat(notFound).isInstanceOf(PayoutResult.Unknown.class);
        assertThat(notFound.toString()).contains("NOT_FOUND").contains("traceId=t-9");
        assertThat(timeout).isEqualTo(new PayoutResult.Unknown("토스 응답 없음: ResourceAccessException"));
    }

    private static String payoutList(String id, String status) {
        return """
                {"version":"2024-06-01","traceId":"t-1","entityType":"payout-list",
                 "entityBody":{"items":[{"id":"%s","refPayoutId":"moyeota-payout-17","destination":"seller_abc","scheduleType":"EXPRESS",
                 "amount":{"currency":"KRW","value":45000},"transactionDescription":"모여타정산","status":"%s","error":null}]}}"""
                .formatted(id, status);
    }
}
