package team.codingforest.moyeota.chat.member.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisZSetCommands.ZAddArgs;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;
import team.codingforest.moyeota.chat.member.domain.PendingReads;
import team.codingforest.moyeota.chat.member.domain.ReadPosition;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class PendingReadsRedis implements PendingReads {

    private static final String KEY_PREFIX = "chat:read:pending:";
    private static final String USERS_KEY = "chat:read:users";
    private static final long TTL_SECONDS = 3_600;   // 꺼내지 못하고 남은 키를 정리함

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean record(ReadPosition position, int maxRooms) {
        byte[] key = bytes(key(position.userId()));
        byte[] room = bytes(String.valueOf(position.chatRoomId()));

        // 확인과 쓰기 사이에 다른 요청이 끼면 상한을 조금 넘을 수 있음 - 없는 방 id 대량 전송을 막는 용도라 허용
        List<Object> state = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.zSetCommands().zScore(key, room);
            connection.zSetCommands().zCard(key);
            return null;
        });
        if (state.get(0) == null && (Long) state.get(1) >= maxRooms) {
            return false;
        }

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            connection.zSetCommands().zAdd(key, position.messageId(), room, ZAddArgs.empty().gt());   // 기존보다 클 때만 바뀜
            connection.keyCommands().expire(key, TTL_SECONDS);
            connection.setCommands().sAdd(bytes(USERS_KEY), bytes(String.valueOf(position.userId())));
            return null;
        });
        return true;
    }

    @Override
    public List<Long> popUsers(int count) {
        List<String> users = redisTemplate.opsForSet().pop(USERS_KEY, count);
        if (users == null) {
            return List.of();
        }
        return users.stream().map(Long::valueOf).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ReadPosition> takeAll(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }

        List<Object> results = redisTemplate.execute(new SessionCallback<>() {
            @Override
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) {
                RedisOperations<String, String> ops = (RedisOperations<String, String>) operations;
                ops.multi();
                for (Long userId : userIds) {
                    ops.opsForZSet().rangeWithScores(key(userId), 0, -1);
                    ops.delete(key(userId));
                }
                ops.opsForSet().remove(USERS_KEY, userIds.stream().map(String::valueOf).toArray());
                return ops.exec();
            }
        });
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<ReadPosition> positions = new ArrayList<>();
        for (int i = 0; i < userIds.size(); i++) {
            Long userId = userIds.get(i);
            Set<TypedTuple<String>> rooms = (Set<TypedTuple<String>>) results.get(i * 2);
            if (rooms == null) {
                continue;
            }
            rooms.forEach(t -> positions.add(
                    new ReadPosition(Long.valueOf(t.getValue()), userId, t.getScore().longValue())));
        }
        return positions;
    }
    @Override
    public long countUsers() {
        Long size = redisTemplate.opsForSet().size(USERS_KEY);
        return size == null ? 0 : size;
    }

    private static String key(Long userId) {
        return KEY_PREFIX + userId;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}