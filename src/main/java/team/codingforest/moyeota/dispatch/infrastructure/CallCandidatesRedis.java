package team.codingforest.moyeota.dispatch.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.dispatch.domain.CallCandidates;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class CallCandidatesRedis implements CallCandidates {
    private static final String PREFIX = "dispatch:candidates:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;


    @Override
    public void register(Long partyId, List<Long> driverIds) {
        String key = PREFIX + partyId;

        redisTemplate.delete(key);
        redisTemplate.opsForSet().add(key, driverIds.stream().map(String::valueOf).toArray(String[]::new));
        redisTemplate.expire(key, TTL);
    }

    @Override
    public boolean contains(Long partyId, Long driverId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(PREFIX + partyId, driverId.toString()));
    }

    @Override
    public long remove(Long partyId, Long driverId) {
        String key = PREFIX + partyId;

        redisTemplate.opsForSet().remove(key, driverId.toString());

        Long size = redisTemplate.opsForSet().size(key);

        return size == null? 0 : size;
    }

    @Override
    public List<Long> findAll(Long partyId) {
        String key = PREFIX + partyId;

        Set<String> members = redisTemplate.opsForSet().members(key);

        if(members == null) return List.of();

        return members.stream().map(Long::valueOf).toList();
    }

    @Override
    public void clear(Long partyId) {
        String key = PREFIX + partyId;

        redisTemplate.delete(key);
    }
}
