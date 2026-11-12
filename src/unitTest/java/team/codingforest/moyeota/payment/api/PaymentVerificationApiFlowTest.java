package team.codingforest.moyeota.payment.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.JpaAuditingConfig;
import team.codingforest.moyeota.common.exception.GlobalExceptionHandler;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.dto.PartySummary;
import team.codingforest.moyeota.payment.client.PortOneClient;
import team.codingforest.moyeota.payment.config.PortOneApiConfig;
import team.codingforest.moyeota.payment.controller.PaymentController;
import team.codingforest.moyeota.payment.dto.CardDetail;
import team.codingforest.moyeota.payment.entity.PaymentMethod;
import team.codingforest.moyeota.payment.repository.PaymentMethodRepository;
import team.codingforest.moyeota.payment.service.PaymentService;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;
import team.codingforest.moyeota.user.auth.presentation.security.CurrentUserArgumentResolver;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 실제 컨트롤러·서비스·JPA·RestClient를 연결한다. 포트원만 로컬 HTTP 서버로 대체한다.
 * H2에서 실제 커밋 후 SQL로 확인한다. PostgreSQL/Flyway 및 실제 PG 승인을 검증하지는 않는다.
 * 인증 사용자는 직접 설정하며 보안 필터의 접근 제어는 별도 테스트 대상이다.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import({JpaAuditingConfig.class, PaymentService.class, PaymentVerificationApiFlowTest.Configuration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentVerificationApiFlowTest {
    private static final String VERIFY = "/api/v1/payments/parties/10/verifications";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired PaymentService service;
    @Autowired PortOneClient client;
    @Autowired PaymentMethodRepository methods;
    @Autowired JdbcTemplate jdbc;
    @Autowired PortOneStub portOne;
    @MockitoBean PartyAccess parties;
    private MockMvc mvc;

    @TestConfiguration
    static class Configuration {
        @Bean(destroyMethod = "close")
        PortOneStub portOneStub(JdbcTemplate jdbc) throws IOException { return new PortOneStub(jdbc); }

        @Bean
        PortOneClient portOneClient(PortOneStub stub) {
            return new PortOneClient(new PortOneApiConfig().portOneRestClient("test-secret", stub.baseUrl()));
        }
    }

    @BeforeEach
    void 준비() {
        for (String table : List.of("payment_cancel", "payment_log", "payment", "payment_group", "card", "payment_method")) {
            jdbc.update("delete from " + table);
        }
        portOne.reset();
        var method = PaymentMethod.registerCard(7L, "bk-test", new CardDetail("테스트 카드", "1234", "VISA"));
        method.selectAsDefault();
        methods.saveAndFlush(method);
        when(parties.findSummary(10L)).thenReturn(Optional.of(new PartySummary(
                10L, 0, 0, 0, 0, "출발", "도착", 3, 12000, 10, null)));
        when(parties.hasMemberOnParty(10L, 7L)).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedPrincipal(7L, UUID.randomUUID()), null, List.of()));
        mvc = MockMvcBuilders.standaloneSetup(new PaymentController(service))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void 인증_정리() { SecurityContextHolder.clearContext(); }

    @Test
    void 검증API는_시작기록_커밋_승인_취소_후_DB에_CANCELED를_커밋한다() throws Exception {
        var result = mvc.perform(post(VERIFY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true)).andReturn();
        long id = JSON.readTree(result.getResponse().getContentAsString()).path("paymentId").asLong();
        var row = jdbc.queryForMap("select status, pg_tx_id from payment where id = ?", id);
        assertThat(row.get("status")).isEqualTo("CANCELED");
        assertThat(row.get("pg_tx_id")).isEqualTo("pg-test");
        String requestId = Long.toString(id);
        assertRequests(requestId, 12000);
        // HTTP 핸들러의 다른 스레드·연결에서도 상태가 보이면 외부 호출 전에 커밋된 것이다.
        assertThat(portOne.requests.get(0).committedStatus()).isEqualTo("PENDING");
        assertThat(portOne.requests.get(1).committedStatus()).isEqualTo("PAID");
        assertThat(jdbc.queryForList("select status from payment_log order by id", String.class))
                .containsExactly("PENDING", "PAID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"REQUESTED", "FAILED"})
    void 취소_미완료이면_502이고_DB는_PAID를_유지한다(String status) throws Exception {
        portOne.cancelBody = cancellation(status, 12000);
        mvc.perform(post(VERIFY)).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("GATEWAY_ERROR"));
        assertDbStatus("PAID");
        assertThat(portOne.requests).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "not-json", "null", "", "{\"cancellation\":{\"status\":\"SUCCEEDED\",\"totalAmount\":1}}"})
    void 잘못된_취소응답은_검증성공이나_CANCELED로_처리하지_않는다(String response) throws Exception {
        portOne.cancelBody = response;
        mvc.perform(post(VERIFY)).andExpect(status().isBadGateway());
        assertDbStatus("PAID");
    }

    @Test
    void 승인_HTTP오류이면_PENDING기록은_남고_취소는_요청하지_않는다() throws Exception {
        portOne.payCode = 500;
        mvc.perform(post(VERIFY)).andExpect(status().isBadGateway());
        assertDbStatus("PENDING");
        assertThat(portOne.requests).hasSize(1);
    }

    @Test
    void 승인응답이_불완전하면_PENDING을_유지한다() throws Exception {
        portOne.payBody = "{\"payment\":{\"pgTxId\":\"pg-test\"}}";
        mvc.perform(post(VERIFY)).andExpect(status().isBadGateway());
        assertDbStatus("PENDING");
        assertThat(portOne.requests).hasSize(1);
    }

    @Test
    void 취소_HTTP오류이면_PAID기록을_유지한다() throws Exception {
        portOne.cancelCode = 500;
        mvc.perform(post(VERIFY)).andExpect(status().isBadGateway());
        assertDbStatus("PAID");
    }

    @Test
    void 방에_참여하지_않으면_결제나_DB기록을_생성하지_않는다() throws Exception {
        when(parties.hasMemberOnParty(10L, 7L)).thenReturn(false);
        mvc.perform(post(VERIFY)).andExpect(status().isForbidden());
        assertThat(portOne.requests).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from payment", Integer.class)).isZero();
    }

    @Test
    void 없는_파티는_404이고_결제를_요청하지_않는다() throws Exception {
        when(parties.findSummary(10L)).thenReturn(Optional.empty());
        mvc.perform(post(VERIFY)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_PARTY_NOT_FOUND"));
        assertThat(portOne.requests).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from payment", Integer.class)).isZero();
    }

    @Test
    void 기본_결제수단이_없으면_404이고_그룹과_결제를_생성하지_않는다() throws Exception {
        jdbc.update("update payment_method set is_default = false");
        mvc.perform(post(VERIFY)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_METHOD_NOT_FOUND"));
        assertThat(portOne.requests).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from payment", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from payment_group", Integer.class)).isZero();
    }

    @Test
    void 인증사용자가_없으면_검증API는_401이다() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(post(VERIFY)).andExpect(status().isUnauthorized());
        assertThat(portOne.requests).isEmpty();
    }

    private void assertDbStatus(String status) {
        assertThat(jdbc.queryForList("select status from payment", String.class)).containsExactly(status);
    }

    private void assertRequests(String id, int amount) {
        assertThat(portOne.requests).hasSize(2);
        var pay = portOne.requests.get(0);
        var cancel = portOne.requests.get(1);
        assertThat(pay.path()).isEqualTo("/payments/" + id + "/billing-key");
        assertThat(cancel.path()).isEqualTo("/payments/" + id + "/cancel");
        for (var request : portOne.requests) {
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.authorization()).isEqualTo("PortOne test-secret");
            assertThat(request.contentType()).startsWith("application/json");
        }
        assertThat(JSON.readTree(pay.body())).isEqualTo(JSON.readTree("""
                {"billingKey":"bk-test","orderName":"모여타 사전 결제","amount":{"total":%d},"currency":"KRW"}
                """.formatted(amount)));
        assertThat(JSON.readTree(cancel.body())).isEqualTo(JSON.readTree("{\"reason\":\"사전 결제 취소\"}"));
    }

    private static String cancellation(String status, int amount) {
        return "{\"cancellation\":{\"status\":\"%s\",\"totalAmount\":%d}}".formatted(status, amount);
    }

    record Request(String method, String path, String authorization, String contentType, String body, String committedStatus) {}

    static class PortOneStub implements AutoCloseable {
        private final HttpServer server;
        final List<Request> requests = new CopyOnWriteArrayList<>();
        volatile int payCode;
        volatile int cancelCode;
        volatile String payBody;
        volatile String cancelBody;

        PortOneStub(JdbcTemplate jdbc) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/payments/", exchange -> {
                try (exchange) {
                    String path = exchange.getRequestURI().getPath();
                    String id = path.split("/")[2];
                    var statuses = jdbc.queryForList("select status from payment where cast(id as varchar) = ?", String.class, id);
                    requests.add(new Request(exchange.getRequestMethod(), path,
                            exchange.getRequestHeaders().getFirst("Authorization"),
                            exchange.getRequestHeaders().getFirst("Content-Type"),
                            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                            statuses.isEmpty() ? null : statuses.getFirst()));
                    boolean pay = path.endsWith("/billing-key");
                    boolean cancel = path.endsWith("/cancel");
                    byte[] body = (pay ? payBody : cancel ? cancelBody : "{}").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(pay ? payCode : cancel ? cancelCode : 404, body.length);
                    exchange.getResponseBody().write(body);
                }
            });
            reset();
            server.start();
        }

        void reset() {
            requests.clear();
            payCode = cancelCode = 200;
            payBody = "{\"payment\":{\"pgTxId\":\"pg-test\",\"paidAt\":\"2026-10-30T00:00:00Z\"}}";
            cancelBody = cancellation("SUCCEEDED", 12000);
        }

        String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        @Override public void close() { server.stop(0); }
    }
}
