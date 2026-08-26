package team.codingforest.moyeota.matching.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.matching.domain.RouteCache;
import team.codingforest.moyeota.matching.domain.RouteEstimate;
import team.codingforest.moyeota.matching.domain.RouteKey;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RouteRedisCache implements RouteCache {

    private static final String PREFIX = "routes:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<RouteEstimate> find(RouteKey key) {
        try {
            String json = redisTemplate.opsForValue().get(PREFIX + key.value());

            if(json == null) return Optional.empty();

            return Optional.of(objectMapper.readValue(json, RouteEstimate.class));
        } catch (Exception e) {
            log.warn("경로 캐시 조회 실패, 네이버 재호출로 대체 key={}", key.value(), e);
            return Optional.empty();
        }
    }

    @Override
    public void save(RouteKey key, RouteEstimate estimate) {
        try {
            redisTemplate.opsForValue().set(PREFIX + key.value(), objectMapper.writeValueAsString(estimate), TTL);
        } catch (Exception e) {
            log.warn("경로 캐시 저장 실패 key={}", key.value(), e);
        }
    }
}
