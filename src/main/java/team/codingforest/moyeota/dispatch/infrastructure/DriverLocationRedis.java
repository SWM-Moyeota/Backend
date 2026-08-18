package team.codingforest.moyeota.dispatch.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.dispatch.domain.DriverLocations;

import java.time.Duration;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DriverLocationRedis implements DriverLocations {

    // TODO 임시로 사용 추후에 시스템 환경변수로 뺄 예정
    private static final String GEO_KEY = "drivers:location";
    private static final String HEARTBEAT_PREFIX = "drivers:heartbeat:";
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void update(Long driverId, double latitude, double longitude) {
        // member -> score 저장, skip List(score 순 정렬) 저장 2개의 구조 유지
        redisTemplate.opsForGeo().add(GEO_KEY, new Point(longitude, latitude), driverId.toString());

        redisTemplate.opsForValue().set(HEARTBEAT_PREFIX + driverId, "1", HEARTBEAT_TTL);
    }

    @Override
    public void remove(Long driverId) {
        redisTemplate.opsForGeo().remove(GEO_KEY, driverId.toString());
        redisTemplate.delete(HEARTBEAT_PREFIX + driverId);
    }

    @Override
    public List<Long> findNearby(double latitude, double longitude, int radiusMeters) {
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().search(
                GEO_KEY,
                GeoReference.fromCoordinate(longitude, latitude),
                new Distance(radiusMeters, Metrics.METERS),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().sortAscending());

        return results.getContent().stream()
                .map(r -> Long.valueOf(r.getContent().getName()))
                .filter(this::isAlive)
                .toList();
    }

    private boolean isAlive(Long driverId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(HEARTBEAT_PREFIX + driverId));
    }
}
