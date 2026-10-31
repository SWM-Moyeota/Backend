package team.codingforest.moyeota.common;

import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.party.PartyController;
import team.codingforest.moyeota.matching.party.PartyService;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 *  SseEmitter 가 complete 되면 WAS 가 같은 요청을 ASYNC 디스패치로 필터 체인에 다시 태운다.
 *  그때는 인증 정보(1회차 스레드의 ThreadLocal)가 없어 인가 검사를 하면 실패하고, 응답은 이미 나가서 401 도 못 쓴다.
 *  ERROR 디스패치처럼 ASYNC 도 인가에서 빼야 한다. @CurrentUser 가 없는 상세 조회로 확인한다 - 인가만 통과하면 404 까지 간다.
 */
@WebMvcTest(PartyController.class)
class SecurityDispatchTypeUnitTest extends ControllerUnitTestSupport {
    private static final Long PARTY_ID = 999L;

    @MockitoBean
    private PartyService partyService;

    @DisplayName("GET /api/v1/matching/rooms/{partyId} - 처음 들어온 요청은 토큰이 없으면 401")
    @Test
    void requestDispatch_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms/{partyId}", PARTY_ID))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized());
        verify(partyService, never()).getPartyDetail(any());
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId} - ASYNC 디스패치는 인가 검사 없이 컨트롤러까지 간다")
    @Test
    void asyncDispatch() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND)).when(partyService).getPartyDetail(eq(PARTY_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms/{partyId}", PARTY_ID).with(dispatchedAs(DispatcherType.ASYNC)))

        /*
         * verify
         */
                .andExpect(status().isNotFound());
        verify(partyService).getPartyDetail(eq(PARTY_ID));
    }

    @DisplayName("GET /api/v1/matching/rooms/{partyId} - ERROR 디스패치는 인가 검사 없이 컨트롤러까지 간다")
    @Test
    void errorDispatch() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND)).when(partyService).getPartyDetail(eq(PARTY_ID));

        /*
         * test
         */
        mockMvc.perform(get("/api/v1/matching/rooms/{partyId}", PARTY_ID).with(dispatchedAs(DispatcherType.ERROR)))

        /*
         * verify
         */
                .andExpect(status().isNotFound());
        verify(partyService).getPartyDetail(eq(PARTY_ID));
    }

    private static RequestPostProcessor dispatchedAs(DispatcherType type) {
        return request -> { request.setDispatcherType(type); return request; };
    }
}
