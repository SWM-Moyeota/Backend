package team.codingforest.moyeota.matching.route.infrastructure;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.route.domain.RouteEstimate;
import team.codingforest.moyeota.matching.route.domain.RouteFinder;
import team.codingforest.moyeota.matching.route.domain.RouteKey;

import java.time.Duration;

/** 부하 테스트 전용. 실제 네이버 호출 없이 느린 경로 API 를 흉내 내서 커넥션 점유를 관측함 */
@Component
@Primary
@Profile("loadtest")
class DelayedRouteFinder implements RouteFinder {
    private static final Duration DELAY = Duration.ofMillis(500);
    private static final RouteEstimate ESTIMATE = new RouteEstimate(15_000, 30, "_p~iF~ps|U_ulLnnqC");

    @Override
    public RouteEstimate find(RouteKey key) {
        try {
            Thread.sleep(DELAY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ESTIMATE;
    }
}
