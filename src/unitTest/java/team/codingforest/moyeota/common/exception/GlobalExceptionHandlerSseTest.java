package team.codingforest.moyeota.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  SSE 클라이언트(Accept: text/event-stream)는 JSON 을 못 받는다. 그쪽에 본문을 실으면 컨버터 협상이 실패해
 *  원래 예외가 서블릿까지 새어 500 이 된다(로그: HttpMediaTypeNotAcceptableException → Request processing failed).
 *  그래서 SSE 요청에만 본문 없이 상태·헤더로 답하고, 나머지는 기존 JSON 계약(GlobalExceptionHandlerTest) 그대로여야 한다.
 */
class GlobalExceptionHandlerSseTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final BusinessException 비멤버 = new BusinessException(TestErrorCode.NOT_PARTY_MEMBER);

    @Test
    void SSE_요청에는_본문_없이_상태와_에러코드_헤더만_보낸다() {
        ResponseEntity<ErrorResponse> res = handler.handleBusiness(비멤버, requestAccepting(MediaType.TEXT_EVENT_STREAM_VALUE));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody()).as("본문이 있으면 event-stream 과 협상 실패 → 500").isNull();
        assertThat(res.getHeaders().getFirst("X-Error-Code")).isEqualTo("NOT_PARTY_MEMBER");
    }

    @Test
    void JSON_요청에는_기존대로_본문을_보낸다() {
        ResponseEntity<ErrorResponse> res = handler.handleBusiness(비멤버, requestAccepting(MediaType.APPLICATION_JSON_VALUE));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().code()).isEqualTo("NOT_PARTY_MEMBER");
    }

    @Test
    void 전체_수용_요청은_본문을_보낸다() {
        // curl · Postman 기본값. JSON 도 받을 수 있으니 SSE 로 취급하면 안 된다
        assertThat(handler.handleBusiness(비멤버, requestAccepting(MediaType.ALL_VALUE)).getBody()).isNotNull();
    }

    @Test
    void event_stream_과_JSON_을_같이_받는_요청은_본문을_보낸다() {
        assertThat(handler.handleBusiness(비멤버, requestAccepting("text/event-stream, application/json")).getBody()).isNotNull();
    }

    @Test
    void Accept_헤더가_없으면_본문을_보낸다() {
        assertThat(handler.handleBusiness(비멤버, new MockHttpServletRequest()).getBody()).isNotNull();
    }

    private static MockHttpServletRequest requestAccepting(String accept) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ACCEPT, accept);
        return request;
    }

    /** 실제 MatchingErrorCode 에 의존하지 않도록 최소 구현 - 이 테스트는 핸들러의 Accept 분기만 본다 */
    enum TestErrorCode implements ErrorCode {
        NOT_PARTY_MEMBER;

        @Override public String getCode() { return name(); }
        @Override public String getMessage() { return "해당 방에 참여하고 있지 않습니다."; }
        @Override public HttpStatus getHttpStatus() { return HttpStatus.FORBIDDEN; }
    }
}
