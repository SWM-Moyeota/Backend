package team.codingforest.moyeota.chat.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.chat.domain.PartyLocation;
import team.codingforest.moyeota.chat.domain.PartyLocations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class PartyLocationRedis implements PartyLocations {

    private static final String COORD_PREFIX = "party:location:";
    private static final String SESSION_PREFIX = "party:sharing:";
    private static final Duration COORD_TTL = Duration.ofMinutes(5);
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void startSession(Long userId, Long partyId) {
        redisTemplate.opsForValue().set(sessionKey(userId, partyId), "1", SESSION_TTL);
    }

    @Override
    public void put(Long userId, Long partyId, PartyLocation partyLocation) {
        String key = coordKey(partyId);

        PartyLocation existing = readCoord(key, userId);

        if (!partyLocation.isNewerThan(existing)) {
            return;
        }

        redisTemplate.opsForHash().put(key, userId.toString(), objectMapper.writeValueAsString(partyLocation));

        redisTemplate.expire(key, COORD_TTL);
    }

    @Override
    public Map<Long, PartyLocation> findAll(Long partyId) {
        Map<Object, Object> map = redisTemplate.opsForHash().entries(coordKey(partyId));

        Map<Long, PartyLocation> result = new HashMap<>();

        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            result.put(
                    Long.parseLong(entry.getKey().toString()),
                    objectMapper.readValue(entry.getValue().toString(), PartyLocation.class)
            );
        }

        return result;
    }

    @Override
    public boolean isSharing(Long userId, Long partyId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(userId, partyId)));
    }

    @Override
    public void stop(Long userId, Long partyId) {
        redisTemplate.opsForHash().delete(coordKey(partyId), userId.toString());
        redisTemplate.delete(sessionKey(userId, partyId));
    }

    private String coordKey(Long partyId) {
        return COORD_PREFIX + partyId;
    }

    private String sessionKey(Long userId, Long partyId) {
        return SESSION_PREFIX + partyId + ":" + userId;
    }

    private PartyLocation readCoord(String key, Long userId) {
        Object raw = redisTemplate.opsForHash().get(key, userId.toString());

        if (raw == null) {
            return null;
        }

        return objectMapper.readValue(raw.toString(), PartyLocation.class);
    }
}
