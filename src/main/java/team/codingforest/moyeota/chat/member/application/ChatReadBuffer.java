package team.codingforest.moyeota.chat.member.application;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import team.codingforest.moyeota.chat.common.config.ChatPrincipal;
import team.codingforest.moyeota.chat.member.domain.ReadPosition;
import team.codingforest.moyeota.chat.member.domain.ReadPositions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 읽음 위치를 서버 메모리에 모았다가 5초마다 한 번에 씀
 * 서버가 비정상 종료되면 아직 안 쓴 최대 5초 분량이 사라짐. 안 읽은 수가 잠깐 더 보이는 정도이고 다음 읽음에서 회복됨
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatReadBuffer {

    private static final int MAX_PENDING_USERS = 10_000;   // 넘으면 모으지 않고 바로 씀

    private final ReadPositions readPositions;
    private final MeterRegistry meterRegistry;

    // 사용자 id → (채팅방 id → 가장 큰 메시지 id)
    private final ConcurrentHashMap<Long, Map<Long, Long>> pending = new ConcurrentHashMap<>();

    @PostConstruct
    void registerGauge() {
        meterRegistry.gauge("chat.read.pending.users", pending, Map::size);
    }

    /**
     * DB 에 바로 쓰지 않고 가장 큰 값만 기억함
     *
     * @param userId 읽은 사람
     * @param chatRoomId 채팅방
     * @param messageId 마지막으로 읽은 메시지
     */
    public void record(Long userId, Long chatRoomId, Long messageId) {
        if (messageId == null) {
            return;
        }
        if (pending.size() >= MAX_PENDING_USERS && !pending.containsKey(userId)) {
            write(List.of(new ReadPosition(chatRoomId, userId, messageId)));   // 비우는 쪽이 밀린 상태 - 쌓지 않음
            return;
        }
        pending.compute(userId, (id, rooms) -> {
            Map<Long, Long> next = rooms == null ? new HashMap<>() : rooms;
            next.merge(chatRoomId, messageId, Math::max);
            return next;
        });
    }

    @Scheduled(fixedDelay = 5_000)
    public void flush() {
        List<ReadPosition> batch = new ArrayList<>();
        for (Long userId : pending.keySet()) {
            batch.addAll(take(userId));
        }
        write(batch);
    }

    /**
     * 목록을 보기 직전처럼 바로 맞아야 할 때 그 사람 것만 먼저 씀
     *
     * @param userId 반영할 사람
     */
    public void flushUser(Long userId) {
        write(take(userId));
    }

    /**
     * 채팅 화면을 나가거나 앱이 튕기면 기다리지 않고 바로 씀
     *
     * @param event 소켓 연결 끊김
     */
    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() instanceof ChatPrincipal principal) {
            flushUser(principal.userId());
        }
    }

    @PreDestroy
    public void flushOnShutdown() {
        flush();
    }

    // remove 와 compute 가 같은 키에서 원자적이라, 꺼낸 뒤 들어온 값은 새 맵에 쌓여 다음 주기에 나감
    private List<ReadPosition> take(Long userId) {
        Map<Long, Long> rooms = pending.remove(userId);
        if (rooms == null) {
            return List.of();
        }
        return rooms.entrySet().stream()
                .map(e -> new ReadPosition(e.getKey(), userId, e.getValue()))
                .toList();
    }

    private void write(List<ReadPosition> batch) {
        if (batch.isEmpty()) {
            return;
        }
        try {
            readPositions.advance(batch);
        } catch (Exception e) {
            log.warn("읽음 위치 반영 실패, 다음 주기에 다시 씀 count={}", batch.size(), e);
            batch.forEach(p -> record(p.userId(), p.chatRoomId(), p.messageId()));
        }
    }
}