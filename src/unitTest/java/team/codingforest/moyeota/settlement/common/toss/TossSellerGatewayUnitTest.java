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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.settlement.account.domain.BusinessInfo;
import team.codingforest.moyeota.settlement.account.domain.BusinessType;
import team.codingforest.moyeota.settlement.account.domain.Seller;
import team.codingforest.moyeota.settlement.account.domain.SellerGatewayException;
import team.codingforest.moyeota.settlement.account.domain.SellerRegistration;
import team.codingforest.moyeota.settlement.account.domain.SellerStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TossSellerGatewayUnitTest {
    private static final String BASE_URL = "https://api.tosspayments.com";
    private static final String SECURITY_KEY = "0123456789abcdef".repeat(4);
    private static final TossPayoutProperties PROPERTIES =
            new TossPayoutProperties(BASE_URL, "test_sk_sample", SECURITY_KEY, Duration.ofSeconds(3), Duration.ofSeconds(10));
    private static final BusinessInfo 사업자 = new BusinessInfo(null, "홍길동", "2208112341", "owner@example.com", "0212345678");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TossJweCipher cipher = new TossJweCipher(SECURITY_KEY, objectMapper);

    private MockRestServiceServer server;
    private TossSellerGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = TossPayoutConfig.authorized(RestClient.builder(), PROPERTIES);
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new TossSellerGateway(new TossPayoutClient(builder.build(), cipher, objectMapper));
    }

    @DisplayName("register - 사업자 정보와 계좌를 토스 형식으로 암호화해 보내고 셀러 id·상태를 돌려준다")
    @Test
    void register() {

        /*
         * settings
         */
        AtomicReference<JsonNode> sent = new AtomicReference<>();
        server.expect(requestTo(BASE_URL + "/v2/sellers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("TossPayments-api-security-mode", "ENCRYPTION"))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andExpect(request -> sent.set(cipher.decrypt(((MockClientHttpRequest) request).getBodyAsString())))
                .andRespond(withSuccess(cipher.encrypt("""
                        {"version":"2022-11-16","traceId":"t-1","entityType":"seller",
                         "entityBody":{"id":"seller_abc","refSellerId":"pa00031","status":"PARTIALLY_APPROVED"}}"""), MediaType.TEXT_PLAIN));

        /*
         * test
         */
        Seller seller = gateway.register(new SellerRegistration("pa00031", BusinessType.CORPORATE, 사업자,
                BankCode.KOOKMIN, "110123456789", "모여타택시"));

        /*
         * verify
         */
        assertThat(seller).isEqualTo(new Seller("seller_abc", SellerStatus.PARTIALLY_APPROVED));
        assertThat(sent.get()).isEqualTo(objectMapper.readTree("""
                {"refSellerId":"pa00031","businessType":"CORPORATE",
                 "company":{"representativeName":"홍길동","businessRegistrationNumber":"2208112341","email":"owner@example.com","phone":"0212345678"},
                 "account":{"bankCode":"06","accountNumber":"110123456789","holderName":"모여타택시"}}"""));
        server.verify();
    }

    @DisplayName("register - 상호가 없으면 company.name 을 null 로 보내지 않고 아예 뺀다")
    @Test
    void register_withoutCompanyName() {

        /*
         * settings
         */
        AtomicReference<JsonNode> sent = new AtomicReference<>();
        server.expect(requestTo(BASE_URL + "/v2/sellers"))
                .andExpect(request -> sent.set(cipher.decrypt(((MockClientHttpRequest) request).getBodyAsString())))
                .andRespond(withSuccess(cipher.encrypt("""
                        {"entityBody":{"id":"seller_abc","status":"PARTIALLY_APPROVED"}}"""), MediaType.TEXT_PLAIN));

        /*
         * test
         */
        gateway.register(new SellerRegistration("pa00031", BusinessType.CORPORATE, 사업자, BankCode.KOOKMIN, "110123456789", "모여타택시"));

        /*
         * verify
         */
        assertThat(sent.get().path("company").has("name")).isFalse();
    }

    @DisplayName("changeAccount - 셀러 id 를 경로에 넣고 본문에는 계좌만 담아 다른 필드가 지워지지 않게 한다")
    @Test
    void changeAccount() {

        /*
         * settings
         */
        AtomicReference<JsonNode> sent = new AtomicReference<>();
        server.expect(requestTo(BASE_URL + "/v2/sellers/seller_abc"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("TossPayments-api-security-mode", "ENCRYPTION"))
                .andExpect(request -> sent.set(cipher.decrypt(((MockClientHttpRequest) request).getBodyAsString())))
                .andRespond(withSuccess(cipher.encrypt("""
                        {"entityBody":{"id":"seller_abc","status":"PARTIALLY_APPROVED"}}"""), MediaType.TEXT_PLAIN));

        /*
         * test
         */
        Seller seller = gateway.changeAccount("seller_abc", BankCode.SHINHAN, "1002003004005", "새예금주");

        /*
         * verify
         */
        assertThat(seller.sellerId()).isEqualTo("seller_abc");
        assertThat(sent.get()).isEqualTo(objectMapper.readTree("""
                {"account":{"bankCode":"88","accountNumber":"1002003004005","holderName":"새예금주"}}"""));
        server.verify();
    }

    @DisplayName("find - 셀러 id 로 조회해 상태를 돌려준다")
    @Test
    void find() {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/sellers/seller_abc"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"entityBody":{"id":"seller_abc","status":"KYC_REQUIRED"}}""", MediaType.APPLICATION_JSON));

        /*
         * test
         */
        Seller seller = gateway.find("seller_abc");

        /*
         * verify
         */
        assertThat(seller).isEqualTo(new Seller("seller_abc", SellerStatus.KYC_REQUIRED));
        server.verify();
    }

    @DisplayName("find - 토스가 셀러를 못 찾으면 토스 코드와 원래 예외를 담은 REJECTED 셀러 예외로 바꿔 던진다")
    @Test
    void find_notFound() {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/sellers/seller_none"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("""
                        {"traceId":"t-9","error":{"code":"NOT_FOUND_SELLER","message":"셀러를 찾을 수 없습니다."}}"""));

        /*
         * test
         */
        SellerGatewayException thrown = catchThrowableOfType(SellerGatewayException.class, () -> gateway.find("seller_none"));

        /*
         * verify
         */
        assertThat(thrown.getReason()).isEqualTo(SellerGatewayException.Reason.REJECTED);
        assertThat(thrown.getCode()).isEqualTo("NOT_FOUND_SELLER");
        assertThat(thrown.getCause()).isInstanceOfSatisfying(TossPayoutException.class, cause -> {
            assertThat(cause.getHttpStatus()).isEqualTo(404);
            assertThat(cause.getTraceId()).isEqualTo("t-9");
        });
    }

    @DisplayName("토스 에러 - 기사가 고칠 수 있는 4xx 만 REJECTED, 수정 막힘은 LOCKED, 우리 쪽 문제·처리 중·장애는 UNAVAILABLE")
    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
            "400, INVALID_REQUEST, REJECTED",
            "404, NOT_FOUND_SELLER, REJECTED",
            "400, FORBIDDEN_SELLER_MODIFY, LOCKED",
            "403, FORBIDDEN_REQUEST, UNAVAILABLE",
            "400, INVALID_ENCRYPTION, UNAVAILABLE",
            "409, PREVIOUS_REQUEST_IN_PROGRESS, UNAVAILABLE",
            "401, UNAUTHORIZED_KEY, UNAVAILABLE",
            "429, TOO_MANY_REQUESTS, UNAVAILABLE",
            "500, INTERNAL_SERVER_ERROR, UNAVAILABLE",
            "503, SERVICE_UNAVAILABLE, UNAVAILABLE"
    })
    void tossError_reason(int status, String code, SellerGatewayException.Reason reason) {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/sellers/seller_abc"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON).body("""
                        {"traceId":"t-1","error":{"code":"%s","message":"실패"}}""".formatted(code)));

        /*
         * test
         */
        SellerGatewayException thrown = catchThrowableOfType(SellerGatewayException.class, () -> gateway.find("seller_abc"));

        /*
         * verify
         */
        assertThat(thrown.getReason()).isEqualTo(reason);
        assertThat(thrown.getCode()).isEqualTo(code);
    }

    @DisplayName("토스 에러 - 4xx 인데 본문을 읽을 수 없어 코드가 없으면 왜 거절됐는지 모르니 UNAVAILABLE")
    @Test
    void tossError_unreadable4xx() {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/sellers/seller_abc"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.TEXT_HTML).body("<html>Bad Request</html>"));

        /*
         * test
         */
        SellerGatewayException thrown = catchThrowableOfType(SellerGatewayException.class, () -> gateway.find("seller_abc"));

        /*
         * verify
         */
        assertThat(thrown.getReason()).isEqualTo(SellerGatewayException.Reason.UNAVAILABLE);
        assertThat(thrown.getCode()).isNull();
    }

    @DisplayName("토스 에러 - 응답 없이 시간이 초과되면 결과를 모르니 UNAVAILABLE 이고 원래 예외를 남긴다")
    @Test
    void tossError_timeout() {

        /*
         * settings
         */
        server.expect(requestTo(BASE_URL + "/v2/sellers"))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out");
                });

        /*
         * test
         */
        SellerGatewayException thrown = catchThrowableOfType(SellerGatewayException.class, () -> gateway.register(
                new SellerRegistration("pa00031", BusinessType.CORPORATE, 사업자, BankCode.KOOKMIN, "110123456789", "모여타택시")));

        /*
         * verify
         */
        assertThat(thrown.getReason()).isEqualTo(SellerGatewayException.Reason.UNAVAILABLE);
        assertThat(thrown.getCode()).isNull();
        assertThat(thrown.getCause()).isInstanceOf(ResourceAccessException.class);
    }
}
