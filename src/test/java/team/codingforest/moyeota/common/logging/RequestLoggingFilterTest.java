package team.codingforest.moyeota.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.KeyValuePair;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import team.codingforest.moyeota.user.api.AuthenticatedPrincipal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        appender = new ListAppender<>();
        appender.start();
        logger().addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger().detachAppender(appender);
        SecurityContextHolder.clearContext();
    }

    private Logger logger() {
        return (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    }

    private Map<String, Object> fields(ILoggingEvent event) {
        List<KeyValuePair> pairs = event.getKeyValuePairs();
        return pairs.stream().collect(Collectors.toMap(p -> p.key, p -> p.value));
    }

    @Test
    void 요청_한_건당_method_path_status_durationMs_를_key_value_로_남긴다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/places");
        request.setQueryString("query=강남역");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(appender.list).hasSize(1);
        Map<String, Object> f = fields(appender.list.get(0));
        assertThat(f).containsEntry("method", "GET")
                .containsEntry("path", "/api/v1/places")
                .containsEntry("status", 200)
                .containsEntry("query", "query=강남역")
                .containsKey("durationMs");
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    void 인증된_요청은_userId_도_함께_남긴다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res) {
                // JwtAuthenticationFilter 가 chain 안에서 인증을 채우는 상황
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(new AuthenticatedPrincipal(42L, UUID.randomUUID()), null, List.of()));
            }
        });

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(fields(appender.list.get(0))).containsEntry("userId", 42L);
    }

    @Test
    void 요청ID_는_MDC_에_실리고_응답_헤더로_돌려주며_끝나면_MDC_에서_지운다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seen = new String[1];
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res) {
                seen[0] = MDC.get(RequestLoggingFilter.MDC_REQUEST_ID);   // 요청 처리 중 다른 로그가 볼 값
            }
        });

        filter.doFilter(request, response, chain);

        assertThat(seen[0]).isNotBlank();
        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo(seen[0]);
        assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    void 클라이언트가_보낸_X_Request_Id_는_그대로_쓴다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "app-abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("app-abc-123");
    }

    @Test
    void 처리되지_않은_예외는_500_으로_기록하고_다시_던진다() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/parties");
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res) {
                throw new IllegalStateException("boom");
            }
        });

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), chain))
                .isInstanceOf(IllegalStateException.class);

        ILoggingEvent event = appender.list.get(0);
        assertThat(fields(event)).containsEntry("status", 500);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(MDC.get(RequestLoggingFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    void health_prometheus_swagger_는_로그를_남기지_않는다() throws Exception {
        for(String path : List.of("/health", "/prometheus", "/swagger-ui/index.html", "/v3/api-docs")) {
            filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), new MockFilterChain());
        }

        assertThat(appender.list).isEmpty();
    }
}
