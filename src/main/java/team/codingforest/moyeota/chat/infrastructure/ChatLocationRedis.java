package team.codingforest.moyeota.chat.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.chat.domain.ChatLocation;
import team.codingforest.moyeota.chat.domain.ChatLocations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ChatLocationRedis implements ChatLocations {

    private static final String COORD_PREFIX = "chat:location:";
    private static final String SESSION_PREFIX = "chat:sharing:";
    private static final Duration COORD_TTL = Duration.ofMinutes(5);
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void startSession(Long userId, Long chatRoomId) {
        redisTemplate.opsForValue().set(sessionKey(userId, chatRoomId), "1", SESSION_TTL);
    }

    @Override
    public void put(Long userId, Long chatRoomId, ChatLocation chatLocation) {
        String key = coordKey(chatRoomId);

        ChatLocation existing = readCoord(key, userId);

        // 백그라운드 요청이 지연 도착해 낡은 좌표로 덮어쓰는 것을 막는다
        if (!chatLocation.isNewerThan(existing)) {
            return;
        }

        redisTemplate.opsForHash().put(key, userId.toString(), objectMapper.writeValueAsString(chatLocation));

        // Hash 전체에 걸리는 TTL - 방치된 키 청소용.
        // 개별 좌표의 신선도는 ChatLocation.isFresh 가 판정한다
        redisTemplate.expire(key, COORD_TTL);
    }

    @Override
    public Map<Long, ChatLocation> findAll(Long chatRoomId) {
        Map<Object, Object> map = redisTemplate.opsForHash().entries(coordKey(chatRoomId));

        Map<Long, ChatLocation> result = new HashMap<>();

        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            result.put(
                    Long.parseLong(entry.getKey().toString()),
                    objectMapper.readValue(entry.getValue().toString(), ChatLocation.class)
            );
        }

        return result;
    }

    @Override
    public boolean isSharing(Long userId, Long chatRoomId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(userId, chatRoomId)));
    }

    @Override
    public void stop(Long userId, Long chatRoomId) {
        redisTemplate.opsForHash().delete(coordKey(chatRoomId), userId.toString());
        redisTemplate.delete(sessionKey(userId, chatRoomId));
    }

    private String coordKey(Long chatRoomId) {
        return COORD_PREFIX + chatRoomId;
    }

    private String sessionKey(Long userId, Long chatRoomId) {
        return SESSION_PREFIX + chatRoomId + ":" + userId;
    }

    private ChatLocation readCoord(String key, Long userId) {
        Object raw = redisTemplate.opsForHash().get(key, userId.toString());

        if (raw == null) {
            return null;
        }

        return objectMapper.readValue(raw.toString(), ChatLocation.class);
    }
}
