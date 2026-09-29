package team.codingforest.moyeota.matching.route;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.matching.route.domain.RouteCache;
import team.codingforest.moyeota.matching.route.domain.RouteEstimate;
import team.codingforest.moyeota.matching.route.domain.RouteFinder;
import team.codingforest.moyeota.matching.route.domain.RouteKey;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {
    private final RouteFinder routeFinder;
    private final RouteCache routeCache;

    public RouteEstimate estimate(double departureLat, double departureLng, double destinationLat, double destinationLng) {
        RouteKey key = RouteKey.of(departureLat, departureLng, destinationLat, destinationLng);

        return routeCache.find(key).orElseGet(() -> {
            RouteEstimate estimate = routeFinder.find(key);
            routeCache.save(key, estimate);
            log.info("경로 산출(네이버) key={}, fare={}, minutes={}", key.value(), estimate.estimateFare(), estimate.estimateTime());
            return estimate;
        });
    }
}
