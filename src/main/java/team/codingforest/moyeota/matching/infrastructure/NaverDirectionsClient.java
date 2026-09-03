package team.codingforest.moyeota.matching.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.domain.RouteEstimate;
import team.codingforest.moyeota.matching.domain.RouteFinder;
import team.codingforest.moyeota.matching.domain.RouteKey;
import team.codingforest.moyeota.matching.domain.exception.MatchingErrorCode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverDirectionsClient implements RouteFinder {
    private static final String OPTION = "traoptimal";

    private final RestClient naverRestClient;

    @Override
    public RouteEstimate find(RouteKey key) {
        NaverDirectionResponse response;
        try {
            response = naverRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/map-direction/v1/driving")
                            .queryParam("start", key.departureLng() + "," + key.departureLat())
                            .queryParam("goal", key.destinationLng() + "," + key.destinationLat())
                            .queryParam("option", OPTION)
                            .build())
                    .retrieve()
                    .body(NaverDirectionResponse.class);
        } catch (RestClientException e) {
            // 외부 장애가 정체불명의 500으로 새지 않게 규격 안(502)으로 변환
            log.warn("네이버 경로 조회 실패 key={}", key, e);
            throw new BusinessException(MatchingErrorCode.ROUTE_SEARCH_FAILED);
        }

        if(response == null || response.code() != 0 || response.route() == null || response.route().isEmpty()) {
            log.warn("경로 없음 key={}, naverMessage={}", key, response == null ? "응답 없음" : response.message());
            throw new BusinessException(MatchingErrorCode.ROUTE_NOT_FOUND);
        }

        NaverDirectionResponse.RoutePath best = response.route().values().iterator().next().get(0);

        NaverDirectionResponse.Summary summary = best.summary();

        return new RouteEstimate(summary.taxiFare(), (int) Math.ceil(summary.duration() / 60_000.0),
                PolylineEncoder.encode(best.path()));
    }
}
