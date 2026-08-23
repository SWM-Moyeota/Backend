package team.codingforest.moyeota.matching.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.matching.domain.RouteEstimate;
import team.codingforest.moyeota.matching.domain.RouteFinder;
import team.codingforest.moyeota.matching.domain.RouteKey;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverDirectionsClient implements RouteFinder {
    private static final String OPTION = "traoptimal";

    private final RestClient naverRestClient;

    @Override
    public RouteEstimate find(RouteKey key) {
        NaverDirectionResponse response = naverRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/map-direction/v1/driving")
                        .queryParam("start", key.departureLng() + "," + key.departureLat())
                        .queryParam("goal", key.destinationLng() + "," + key.destinationLat())
                        .queryParam("option", OPTION)
                        .build())
                .retrieve()
                .body(NaverDirectionResponse.class);

        if(response == null || response.code() != 0 || response.route() == null || response.route().isEmpty()) {
            throw new IllegalArgumentException("경로를 찾을 수 없습니다. " + (response == null? "" : response.message()));
        }

        NaverDirectionResponse.RoutePath best = response.route().values().iterator().next().get(0);

        NaverDirectionResponse.Summary summary = best.summary();

        return new RouteEstimate(summary.taxiFare(), (int) Math.ceil(summary.duration() / 60_000.0),
                PolylineEncoder.encode(best.path()));
    }
}
