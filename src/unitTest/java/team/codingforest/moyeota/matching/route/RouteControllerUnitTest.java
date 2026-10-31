package team.codingforest.moyeota.matching.route;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import team.codingforest.moyeota.common.ControllerUnitTestSupport;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.route.domain.RouteEstimate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteController.class)
class RouteControllerUnitTest extends ControllerUnitTestSupport {
    private static final String INVALID_REQUEST = "INVALID_REQUEST";
    private static final String BODY = """
            {
              "departureLat": 37.4979,
              "departureLng": 127.0276,
              "destinationLat": 37.3948,
              "destinationLng": 127.1112
            }
            """;

    @MockitoBean
    private RouteService routeService;

    @DisplayName("POST /api/v1/matching/routes - 좌표를 순서대로 넘기고 예상 요금·시간·경로를 돌려준다")
    @Test
    void preView() throws Exception {

        /*
         * settings
         */
        doReturn(new RouteEstimate(12000, 25, "encoded-path")).when(routeService).estimate(eq(37.4979), eq(127.0276), eq(37.3948), eq(127.1112));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/routes").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(BODY))

        /*
         * verify
         */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimateFare").value(12000))
                .andExpect(jsonPath("$.estimateTime").value(25))
                .andExpect(jsonPath("$.path").value("encoded-path"));
        verify(routeService).estimate(eq(37.4979), eq(127.0276), eq(37.3948), eq(127.1112));
    }

    @DisplayName("POST /api/v1/matching/routes - 토큰이 없으면 401")
    @Test
    void preView_noToken() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/routes").contentType(MediaType.APPLICATION_JSON).content(BODY))

        /*
         * verify
         */
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER005"));
        verifyNoInteractions(routeService);
    }

    @DisplayName("POST /api/v1/matching/routes - 본문이 없으면 400")
    @Test
    void preView_noBody() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/routes").with(loginUser()).contentType(MediaType.APPLICATION_JSON))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(routeService);
    }

    @DisplayName("POST /api/v1/matching/routes - JSON 이 깨졌으면 400")
    @Test
    void preView_malformedJson() throws Exception {

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/routes").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content("{\"departureLat\": "))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(routeService);
    }

    @DisplayName("POST /api/v1/matching/routes - 좌표가 누락되면 400 이고 서비스를 부르지 않는다")
    @Test
    void preView_missingCoordinate() throws Exception {

        /*
         * settings
         */
        final String body = """
                {
                  "departureLng": 127.0276,
                  "destinationLat": 37.3948,
                  "destinationLng": 127.1112
                }
                """;

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/routes").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(routeService);
    }

    @DisplayName("POST /api/v1/matching/routes - 좌표가 null 이면 400 이고 서비스를 부르지 않는다")
    @Test
    void preView_nullCoordinate() throws Exception {

        /*
         * settings
         */
        final String body = """
                {
                  "departureLat": null,
                  "departureLng": 127.0276,
                  "destinationLat": 37.3948,
                  "destinationLng": 127.1112
                }
                """;

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/routes").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(routeService);
    }

    @DisplayName("POST /api/v1/matching/routes - 좌표가 숫자가 아니면 400 이고 서비스를 부르지 않는다")
    @Test
    void preView_coordinateNotNumber() throws Exception {

        /*
         * settings
         */
        final String body = """
                {
                  "departureLat": "abc",
                  "departureLng": 127.0276,
                  "destinationLat": 37.3948,
                  "destinationLng": 127.1112
                }
                """;

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/routes").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(body))

        /*
         * verify
         */
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST));
        verifyNoInteractions(routeService);
    }

    @DisplayName("POST /api/v1/matching/routes - 서비스의 비즈니스 예외를 상태코드와 오류 코드로 바꾼다")
    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "OUT_OF_SERVICE_AREA, 400",
            "INVALID_ROUTE_ESTIMATE, 400",
            "ROUTE_SEARCH_FAILED, 502"
    })
    void preView_businessException(MatchingErrorCode errorCode, int expectedStatus) throws Exception {

        /*
         * settings
         */
        doThrow(new BusinessException(errorCode)).when(routeService).estimate(eq(37.4979), eq(127.0276), eq(37.3948), eq(127.1112));

        /*
         * test
         */
        mockMvc.perform(post("/api/v1/matching/routes").with(loginUser()).contentType(MediaType.APPLICATION_JSON).content(BODY))

        /*
         * verify
         */
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(errorCode.name()))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()));
    }
}
