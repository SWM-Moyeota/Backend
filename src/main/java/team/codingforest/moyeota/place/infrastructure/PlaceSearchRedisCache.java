package team.codingforest.moyeota.place.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.place.domain.Place;
import team.codingforest.moyeota.place.domain.PlaceSearchCache;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 카카오 장소 검색 결과 Redis 캐시. RouteRedisCache 와 같은 방식 - Redis 장애는 캐시 미스로 취급하고 외부 API 로 간다.
 * TTL 은 짧게 둔다: 카카오 로컬 API 약관상 검색 결과의 장기 저장이 제한되고, 신규 상호 반영도 늦어지면 안 된다.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PlaceSearchRedisCache implements PlaceSearchCache {

    private static final String PREFIX = "place:search:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<List<Place>> find(String normalizedQuery) {
        try {
            String json = redisTemplate.opsForValue().get(PREFIX + normalizedQuery);

            if(json == null) return Optional.empty();

            List<Place> places = objectMapper.readerForListOf(Place.class).readValue(json);
            return Optional.of(places);
        } catch (Exception e) {
            log.warn("장소 검색 캐시 조회 실패, 카카오 재호출로 대체 query={}", normalizedQuery, e);
            return Optional.empty();
        }
    }

    @Override
    public void save(String normalizedQuery, List<Place> places) {
        try {
            redisTemplate.opsForValue().set(PREFIX + normalizedQuery, objectMapper.writeValueAsString(places), TTL);
        } catch (Exception e) {
            log.warn("장소 검색 캐시 저장 실패 query={}", normalizedQuery, e);
        }
    }
}
