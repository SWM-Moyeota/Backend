package team.codingforest.moyeota.matching.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartySseController.class)
class PartySseControllerUnitTest extends ControllerUnitTestSupport {
    private static final Long PARTY_ID = 100L;
    private static final String EVENTS_URL = "/api/v1/matching/rooms/{partyId}/events";

    @MockitoBean
    private PartySseService partySseService;

    @DisplayName("GET /api/v1/matching/rooms/{partyId}/events - 토큰의 사용자로 구독하고 비동기 event-stream 을 연다")
    @Test
    void events() throws Exception {

        /*
         * settings
         */
        doReturn(new SseEmitter(0L)).when(partySseService).subscribe(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        MvcResult result = mockMvc.perform(get(EVENTS_URL, PARTY_ID).with(loginUser()).accept(MediaType.TEXT_EVENT_STREAM))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        verify(partySseService).subscribe(eq(PARTY_ID), eq(USER_ID));
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId}/events - Accept 헤더가 없어도 구독된다")
    @Test
    void events_noAcceptHeader() throws Exception {

        /*
         * settings
         */
        doReturn(new SseEmitter(0L)).when(partySseService).subscribe(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get(EVENTS_URL, PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());
        verify(partySseService).subscribe(eq(PARTY_ID), eq(USER_ID));
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId}/events - emitter 로 보낸 이벤트가 응답에 실린다")
    @Test
    void events_sendEvent() throws Exception {

        /*
         * settings
         */
        SseEmitter emitter = new SseEmitter(0L);

        doReturn(emitter).when(partySseService).subscribe(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        MvcResult result = mockMvc.perform(get(EVENTS_URL, PARTY_ID).with(loginUser()).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        emitter.send(SseEmitter.event().name("closed").data("{\"status\":\"FINISHED\"}"));

        /*
         * verify
         */
        assertThat(result.getResponse().getContentAsString()).contains("event:closed").contains("data:{\"status\":\"FINISHED\"}");
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId}/events - emitter 가 끝난 뒤의 ASYNC 디스패치도 200 으로 끝난다")
    @Test
    void events_asyncDispatchAfterComplete() throws Exception {

        /*
         * settings
         */
        SseEmitter emitter = new SseEmitter(0L);

        doReturn(emitter).when(partySseService).subscribe(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        MvcResult result = mockMvc.perform(get(EVENTS_URL, PARTY_ID).with(loginUser()).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();
        emitter.complete();

        /*
         * verify
         */
        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk());
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId}/events - 토큰이 없으면 401 이고 본문은 JSON 이다")
    @Test
    void events_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(EVENTS_URL, PARTY_ID).accept(MediaType.TEXT_EVENT_STREAM))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(partySseService);
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId}/events - JSON 만 받는 요청은 406 이고 서비스를 부르지 않는다")
    @Test
    void events_acceptJsonOnly() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(EVENTS_URL, PARTY_ID).with(loginUser()).accept(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isNotAcceptable());
        verifyNoInteractions(partySseService);
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId}/events - partyId 가 숫자가 아니면 SSE 요청에도 400 이고 서비스를 부르지 않는다")
    @Test
    void events_invalidPartyId() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(EVENTS_URL, "abc").with(loginUser()).accept(MediaType.TEXT_EVENT_STREAM))

        /*
         * verify
         */
                .andExpect(status().isBadRequest());
        verifyNoInteractions(partySseService);
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId}/events - SSE 요청의 비즈니스 예외는 본문 없이 상태와 X-Error-Code 로 답한다")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "NOT_PARTY_MEMBER, 403",
            "PARTY_NOT_FOUND, 404"
    })
    void events_businessException(MatchingErrorCode errorCode, int expectedStatus) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(partySseService).subscribe(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get(EVENTS_URL, PARTY_ID).with(loginUser()).accept(MediaType.TEXT_EVENT_STREAM))

        /*
         * verify
         */
                .andExpect(status().is(expectedStatus))
                .andExpect(header().string("X-Error-Code", errorCode.name()))
                .andExpect(content().string(""));
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId}/events - Accept 헤더가 없는 요청의 비즈니스 예외는 JSON 본문으로 답한다")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "NOT_PARTY_MEMBER, 403",
            "PARTY_NOT_FOUND, 404"
    })
    void events_businessExceptionNoAcceptHeader(MatchingErrorCode errorCode, int expectedStatus) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(partySseService).subscribe(eq(PARTY_ID), eq(USER_ID));

        /*
         * test
         */
        mockMvc.perform(get(EVENTS_URL, PARTY_ID).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().is(expectedStatus))
                .andExpect(header().doesNotExist("X-Error-Code"))
                .andExpect(jsonPath("$.code").value(errorCode.name()));
    }
}
