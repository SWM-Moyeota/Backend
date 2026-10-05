package team.codingforest.moyeota.user.auth.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.user.auth.domain.PrincipalCache;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
@RequiredArgsConstructor
public class PrincipalRedisCache implements PrincipalCache {

    static final String PREFIX = "auth:principal:";

    static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<Long> findUserId(UUID publicId) {
        try {
            String value = redisTemplate.opsForValue().get(PREFIX + publicId);

            if(value == null) return Optional.empty();

            return Optional.of(Long.valueOf(value));
        } catch (Exception e) {
            log.warn("인증 캐시 조회 실패, DB 조회로 대체 publicId={}", publicId, e);
            return Optional.empty();
        }
    }

    @Override
    public void save(UUID publicId, Long userId) {
        try {
            redisTemplate.opsForValue().set(PREFIX + publicId, String.valueOf(userId), TTL);
        } catch (Exception e) {
            log.warn("인증 캐시 저장 실패 publicId={}", publicId, e);
        }
    }
}
