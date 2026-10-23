package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import team.codingforest.moyeota.common.exception.GlobalExceptionHandler;
import team.codingforest.moyeota.matching.party.completion.DispatchCompletionPolicy;
import team.codingforest.moyeota.matching.route.RouteService;
import team.codingforest.moyeota.matching.route.domain.RouteEstimate;
import team.codingforest.moyeota.user.api.CurrentUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PartyControllerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        PartyServiceTest.RecordingEventPublisher events = new PartyServiceTest.RecordingEventPublisher();
        RouteService routeService = new RouteService(
                key -> new RouteEstimate(12000, 25, "_p~iF~ps|U_ulLnnqC"),
                new RouteCacheTest());
        PartyService service = new PartyService(new PartyJpaTest(), events, routeService,
                new PartyServiceTest.FakeDriverAccess(), new FakeUserAccess(), new DispatchCompletionPolicy(events),
                (name, task) -> task.run(), new PartyServiceTest.RecordingPartyChangeNotifier(),
                new PartyServiceTest.FakeWalkRouteFinder());

        mvc = MockMvcBuilders.standaloneSetup(new PartyController(service))
                .setCustomArgumentResolvers(new StubCurrentUserResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 옛_앱이_반경을_보내도_무시하고_고정값으로_방을_만든다() throws Exception {
        String body = """
                {"departureLat": 37.4979, "departureLng": 127.0276,
                 "destinationLat": 37.3948, "destinationLng": 127.1112,
                 "departure": "강남역", "destination": "판교역", "capacity": 3,
                 "departureRadius": 100, "destinationRadius": 100}
                """;

        mvc.perform(post("/api/v1/matching/rooms").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departureRadius").value(500))
                .andExpect(jsonPath("$.destinationRadius").value(500));
    }

    /** 인증 없이 @CurrentUser=1 주입 */
    static class StubCurrentUserResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUser.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                      NativeWebRequest request, WebDataBinderFactory binderFactory) {
            return 1L;
        }
    }
}
