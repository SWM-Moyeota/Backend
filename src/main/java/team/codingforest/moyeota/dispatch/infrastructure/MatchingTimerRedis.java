package team.codingforest.moyeota.dispatch.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.dispatch.domain.MatchingTimers;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MatchingTimerRedis implements MatchingTimers {
    private static final String PREFIX = "dispatch:matching-started:";
    private static final Duration TTL = Duration.ofMinutes(3);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void start(Long partyId) {
        redisTemplate.opsForValue().set(PREFIX + partyId, String.valueOf(Instant.now().toEpochMilli()), TTL);
    }

    @Override
    public Optional<Duration> elapsed(Long partyId) {
        String value = redisTemplate.opsForValue().get(PREFIX + partyId);

        if(value == null) return Optional.empty();

        return Optional.of(Duration.between(Instant.ofEpochMilli(Long.parseLong(value)), Instant.now()));
    }

    @Override
    public void clear(Long partyId) {
        redisTemplate.delete(PREFIX + partyId);
    }
}
