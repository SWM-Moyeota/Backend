package team.codingforest.moyeota.chat.member.infrastructure;

import team.codingforest.moyeota.chat.member.domain.PendingReads;
import team.codingforest.moyeota.chat.member.domain.ReadPosition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 장애 동안만 쓰는 이 서버 메모리 버퍼
 * 서버끼리 공유하지 않으므로 장애 중에는 다른 서버에 쌓인 읽음이 최대 5초 늦게 보임
 * 빈이 아님 - PendingReads 빈이 둘이 되지 않게 ChatReadBuffer 가 직접 만들어 씀
 */
public class PendingReadsMemory implements PendingReads {

    // 사용자 id → (채팅방 id → 가장 큰 메시지 id)
    private final ConcurrentHashMap<Long, Map<Long, Long>> pending = new ConcurrentHashMap<>();

    @Override
    public boolean record(ReadPosition position, int maxRooms) {
        boolean[] recorded = {true};
        pending.compute(position.userId(), (ignored, rooms) -> {
            Map<Long, Long> next = rooms == null ? new HashMap<>() : rooms;
            if (!next.containsKey(position.chatRoomId()) && next.size() >= maxRooms) {
                recorded[0] = false;
                return rooms;   // 처음 보는 사람이면 null 이 그대로 돌아가 키를 만들지 않음
            }
            next.merge(position.chatRoomId(), position.messageId(), Math::max);
            return next;
        });
        return recorded[0];
    }

    // 꺼낸 사람은 바로 뒤 take 에서 지워짐 - 서버 하나만 보는 저장소라 중복 걱정이 없음
    @Override
    public List<Long> popUsers(int count) {
        return pending.keySet().stream().limit(count).toList();
    }

    // remove 와 compute 가 같은 키에서 원자적이라, 꺼낸 뒤 들어온 값은 새 맵에 쌓여 다음 주기에 나감
    @Override
    public List<ReadPosition> take(Long userId) {
        Map<Long, Long> rooms = pending.remove(userId);
        if (rooms == null) {
            return List.of();
        }
        return rooms.entrySet().stream()
                .map(e -> new ReadPosition(e.getKey(), userId, e.getValue()))
                .toList();
    }

    @Override
    public long countUsers() {
        return pending.size();
    }
}