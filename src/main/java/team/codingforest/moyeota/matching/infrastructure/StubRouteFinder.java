package team.codingforest.moyeota.matching.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.matching.domain.RouteEstimate;
import team.codingforest.moyeota.matching.domain.RouteFinder;
import team.codingforest.moyeota.matching.domain.RouteKey;

import java.util.List;

/**
 * 부하테스트용 경로 조회 스텁. 네이버 Directions 를 부르지 않고 직선거리로 요금·시간을 추정한다.
 * 실제 호출은 쿼터를 소진하고 외부 지연이 섞여 앱 자체의 병목을 가린다.
 */
@Slf4j
@Profile("loadtest")
@Primary
@Component
public class StubRouteFinder implements RouteFinder {
    private static final int BASE_FARE = 4_800;
    private static final int FARE_PER_KM = 1_000;
    private static final double AVG_SPEED_KMH = 25.0;

    @Override
    public RouteEstimate find(RouteKey key) {
        double km = haversineKm(key.departureLat(), key.departureLng(), key.destinationLat(), key.destinationLng());
        int fare = BASE_FARE + (int) Math.round(km * FARE_PER_KM);
        int minutes = Math.max(1, (int) Math.ceil(km / AVG_SPEED_KMH * 60));
        String path = PolylineEncoder.encode(List.of(
                List.of(key.departureLng(), key.departureLat()),
                List.of(key.destinationLng(), key.destinationLat())));
        return new RouteEstimate(fare, minutes, path);
    }

    private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6_371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
