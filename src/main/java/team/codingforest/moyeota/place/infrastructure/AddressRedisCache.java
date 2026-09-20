package team.codingforest.moyeota.place.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.place.domain.Address;
import team.codingforest.moyeota.place.domain.AddressCache;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

/** 역지오코딩 주소 Redis 캐시. 주소는 거의 바뀌지 않으므로 TTL 을 길게 둔다. */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AddressRedisCache implements AddressCache {

    private static final String PREFIX = "place:address:";
    private static final Duration TTL = Duration.ofDays(1);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<Address> find(double latitude, double longitude) {
        try {
            String json = redisTemplate.opsForValue().get(key(latitude, longitude));

            if(json == null) return Optional.empty();

            return Optional.of(objectMapper.readValue(json, Address.class));
        } catch (Exception e) {
            log.warn("주소 캐시 조회 실패, 네이버 재호출로 대체 lat={}, lng={}", latitude, longitude, e);
            return Optional.empty();
        }
    }

    @Override
    public void save(double latitude, double longitude, Address address) {
        try {
            redisTemplate.opsForValue().set(key(latitude, longitude), objectMapper.writeValueAsString(address), TTL);
        } catch (Exception e) {
            log.warn("주소 캐시 저장 실패 lat={}, lng={}", latitude, longitude, e);
        }
    }

    private String key(double latitude, double longitude) {
        return PREFIX + latitude + "," + longitude;
    }
}
