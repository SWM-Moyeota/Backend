package team.codingforest.moyeota.place.search;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.place.exception.PlaceErrorCode;
import team.codingforest.moyeota.place.search.dto.PlaceSearchListResponse;
import team.codingforest.moyeota.place.search.dto.PlaceSearchResponse;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlaceSearchController.class)
class PlaceSearchControllerUnitTest extends ControllerUnitTestSupport {
    private static final String URL = "/api/v1/places";
    private static final String QUERY = "강남역";

    @MockitoBean
    private PlaceSearchService service;

    @DisplayName("GET /places - 쿼리의 검색어로 찾은 결과를 필드 그대로 반환")
    @Test
    void search() throws Exception {

        /*
         * settings
         */
        final PlaceSearchListResponse response = new PlaceSearchListResponse(List.of(
                new PlaceSearchResponse("강남역 2호선", "서울 강남구 강남대로 396", 37.4979, 127.0276)
        ));
        doReturn(response).when(service).search(eq(QUERY));

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()).param("query", QUERY))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list.length()").value(1))
                .andExpect(jsonPath("$.list[0].name").value("강남역 2호선"))
                .andExpect(jsonPath("$.list[0].roadName").value("서울 강남구 강남대로 396"))
                .andExpect(jsonPath("$.list[0].latitude").value(37.4979))
                .andExpect(jsonPath("$.list[0].longitude").value(127.0276));
        verify(service).search(eq(QUERY));
    }

    @DisplayName("GET /places - 결과가 없으면 빈 배열")
    @Test
    void search_noResult() throws Exception {

        /*
         * settings
         */
        doReturn(new PlaceSearchListResponse(List.of())).when(service).search(eq(QUERY));

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()).param("query", QUERY))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list.length()").value(0));
    }

    @DisplayName("GET /places - 토큰이 없으면 401")
    @Test
    void search_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(URL).param("query", QUERY))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(service);
    }

    @DisplayName("GET /places - query 파라미터가 없으면 400")
    @Test
    void search_noQuery() throws Exception {

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()))

        /*
         * verify
         */
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @DisplayName("GET /places - 빈 검색어는 서비스의 SEARCH_QUERY_EMPTY 가 400 으로 나간다")
    @Test
    void search_emptyQuery() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(PlaceErrorCode.SEARCH_QUERY_EMPTY)).when(service).search(eq(""));

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()).param("query", ""))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_QUERY_EMPTY"))
                .andExpect(jsonPath("$.message").value(PlaceErrorCode.SEARCH_QUERY_EMPTY.getMessage()));
    }

    @DisplayName("GET /places - 외부 API 실패면 502")
    @Test
    void search_failed() throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(PlaceErrorCode.PLACE_SEARCH_FAILED)).when(service).search(eq(QUERY));

        /*
         * test
         */
        mockMvc.perform(get(URL).with(loginUser()).param("query", QUERY))

        /*
         * verify
         */
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PLACE_SEARCH_FAILED"));
    }
}
