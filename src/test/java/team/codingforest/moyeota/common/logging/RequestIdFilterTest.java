package team.codingforest.moyeota.common.logging;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /** 필터 체인 안(컨트롤러 시점)에서 MDC 에 무엇이 있었는지 잡아 둔다 */
    private AtomicReference<String> mdcDuringChain(MockHttpServletRequest request, MockHttpServletResponse response) throws ServletException, IOException {
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(MDC.get(LogFields.REQUEST_ID));
            }
        });
        return seen;
    }

    @Test
    void 헤더가_없으면_요청_id를_만들어_MDC와_응답_헤더에_싣는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/places");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = mdcDuringChain(request, response).get();

        assertThat(seen).isNotBlank();
        assertThat(response.getHeader(LogFields.REQUEST_ID_HEADER)).isEqualTo(seen);   // 앱이 받은 id 로 서버 로그를 찾는다
    }

    @Test
    void 클라이언트가_보낸_요청_id는_그대로_쓴다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/places");
        request.addHeader(LogFields.REQUEST_ID_HEADER, "app-7f3a9c2e-0001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = mdcDuringChain(request, response).get();

        assertThat(seen).isEqualTo("app-7f3a9c2e-0001");
        assertThat(response.getHeader(LogFields.REQUEST_ID_HEADER)).isEqualTo("app-7f3a9c2e-0001");
    }

    @Test
    void 이상한_형식의_요청_id는_버리고_새로_만든다() throws Exception {
        // 로그 저장소 쿼리에 그대로 들어가는 값이라 허용 문자를 제한한다
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/places");
        request.addHeader(LogFields.REQUEST_ID_HEADER, "<script>alert(1)</script>");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String seen = mdcDuringChain(request, response).get();

        assertThat(seen).isNotEqualTo("<script>alert(1)</script>").matches("[A-Za-z0-9-]+");
    }

    @Test
    void 요청이_끝나면_MDC를_비운다() throws Exception {
        // 톰캣 스레드는 재사용된다 - 안 비우면 다음 요청 로그에 남의 user_id 가 붙는다
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/places");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                MDC.put(LogFields.USER_ID, "42");
            }
        });

        assertThat(MDC.get(LogFields.REQUEST_ID)).isNull();
        assertThat(MDC.get(LogFields.USER_ID)).isNull();
    }

    @Test
    void 처리_중_예외가_나도_MDC를_비우고_예외는_그대로_올린다() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/places");

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new IllegalStateException("터짐");
            }
        })).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(LogFields.REQUEST_ID)).isNull();
    }
}
