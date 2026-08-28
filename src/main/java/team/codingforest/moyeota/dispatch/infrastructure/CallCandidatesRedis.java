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
    private static final String REJECTED_PREFIX = "dispatch:rejected:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

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
        redisTemplate.delete(PREFIX + partyId);
        redisTemplate.delete(REJECTED_PREFIX + partyId);
    }

    @Override
    public void markRejected(Long partyId, Long driverId) {
        String key = REJECTED_PREFIX + partyId;

        redisTemplate.opsForSet().add(key, driverId.toString());
        redisTemplate.expire(key, TTL);
    }

    @Override
    public List<Long> findRejected(Long partyId) {
        Set<String> members = redisTemplate.opsForSet().members(REJECTED_PREFIX + partyId);

        if(members == null) return List.of();

        return members.stream().map(Long::valueOf).toList();
    }

    @Override
    public void add(Long partyId, List<Long> driverIds) {
        String key = PREFIX + partyId;

        redisTemplate.opsForSet().add(key, driverIds.stream().map(String::valueOf).toArray(String[]::new));
        redisTemplate.expire(key, TTL);   // 탐색이 이어지는 동안 TTL 연장
    }
}
