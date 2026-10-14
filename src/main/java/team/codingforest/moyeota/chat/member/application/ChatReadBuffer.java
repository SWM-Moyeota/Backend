package team.codingforest.moyeota.chat.member.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import team.codingforest.moyeota.chat.common.config.ChatPrincipal;
import team.codingforest.moyeota.chat.member.domain.PendingReads;
import team.codingforest.moyeota.chat.member.domain.ReadPosition;
import team.codingforest.moyeota.chat.member.domain.ReadPositions;
import team.codingforest.moyeota.chat.member.infrastructure.PendingReadsMemory;

import java.util.ArrayList;
import java.util.List;

/**
 * 읽음 위치를 Redis 에 모았다가 5초마다 한 번에 씀
 * 어느 서버에서 읽었든 같은 곳에 모이므로 목록 직전 flushUser 가 다른 서버에 쌓인 읽음도 반영함
 * Redis 가 실패하면 잠시 이 서버 메모리에 모음. DB 부하는 평소와 같고, 장애 중에만 서버 간 반영이 최대 5초 늦어짐
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatReadBuffer {

    private static final int MAX_ROOMS_PER_USER = 50;      // 한 사람이 5초 안에 읽음을 보낼 방 수의 현실적인 상한. 없는 방 id 를 대량으로 보내 메모리를 늘리는 것을 막음
    private static final int NO_LIMIT = Integer.MAX_VALUE;
    private static final int FLUSH_BATCH = 500;
    private static final long REDIS_SKIP_MILLIS = 10_000;  // 실패 뒤 이만큼은 Redis 를 부르지 않음 - 요청마다 타임아웃(1초)을 기다리지 않게

    private final PendingReads pendingReads;
    private final ReadPositions readPositions;
    private final MeterRegistry meterRegistry;

    private final PendingReads fallback = new PendingReadsMemory();
    private volatile long skipRedisUntil = 0;

    @PostConstruct
    void registerGauges() {
        Gauge.builder("chat.read.pending.users", this, ChatReadBuffer::sharedPendingUsers)
                .tag("store", "redis")
                .register(meterRegistry);
        Gauge.builder("chat.read.pending.users", fallback, PendingReads::countUsers)
                .tag("store", "local")
                .register(meterRegistry);
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

        ReadPosition position = new ReadPosition(chatRoomId, userId, messageId);
        if (redisSkipped()) {
            recordOrWrite(fallback, position);
            return;
        }

        try {
            recordOrWrite(pendingReads, position);
        } catch (DataAccessException e) {
            skipRedis(e);
            recordOrWrite(fallback, position);
        }
    }

    // 서버마다 돌지만 popUsers 가 사람을 나눠 꺼내 같은 사람을 두 서버가 쓰지 않음
    @Scheduled(fixedDelay = 5_000)
    public void flush() {
        drain(fallback);   // 장애 중에 이 서버에 모인 것 - Redis 가 살아난 뒤에도 남은 것을 비움

        if (redisSkipped()) {
            return;
        }
        try {
            drain(pendingReads);
        } catch (DataAccessException e) {
            skipRedis(e);
        }
    }

    /**
     * 목록을 보기 직전처럼 바로 맞아야 할 때 그 사람 것만 먼저 씀
     *
     * @param userId 반영할 사람
     */
    public void flushUser(Long userId) {
        List<ReadPosition> batch = new ArrayList<>(fallback.take(userId));

        if (!redisSkipped()) {
            try {
                batch.addAll(pendingReads.take(userId));
            } catch (DataAccessException e) {
                skipRedis(e);
            }
        }
        write(batch);
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

    // Redis 에 있는 것은 다른 서버가 가져가지만 이 서버 메모리에 있는 것은 꺼지면 사라짐
    @PreDestroy
    public void flushOnShutdown() {
        drain(fallback);
    }

    private void recordOrWrite(PendingReads store, ReadPosition position) {
        if (!store.record(position, MAX_ROOMS_PER_USER)) {
            write(List.of(position));   // 방이 비정상적으로 많음
        }
    }

    private void drain(PendingReads store) {
        List<Long> users;
        do {
            users = store.popUsers(FLUSH_BATCH);
            if (!write(store.takeAll(users))) {
                return;
            }
        } while (users.size() == FLUSH_BATCH);
    }

    private boolean write(List<ReadPosition> batch) {
        if (batch.isEmpty()) {
            return true;
        }
        try {
            readPositions.advance(batch);
            return true;
        } catch (Exception e) {
            log.warn("읽음 위치 반영 실패, 다음 주기에 다시 씀 count={}", batch.size(), e);
            requeue(batch);
            return false;
        }
    }

    // 상한을 검사하지 않음 - 검사하면 상한에서 write 로 되돌아가 재귀가 끝나지 않음
    private void requeue(List<ReadPosition> batch) {
        if (!redisSkipped()) {
            try {
                batch.forEach(p -> pendingReads.record(p, NO_LIMIT));
                return;
            } catch (DataAccessException e) {
                skipRedis(e);
            }
        }
        batch.forEach(p -> fallback.record(p, NO_LIMIT));   // 일부가 Redis 에 이미 들어갔어도 큰 값만 남아 겹쳐도 됨
    }

    private boolean redisSkipped() {
        return System.currentTimeMillis() < skipRedisUntil;
    }

    private void skipRedis(DataAccessException e) {
        skipRedisUntil = System.currentTimeMillis() + REDIS_SKIP_MILLIS;
        log.warn("읽음 버퍼 Redis 실패, {}ms 동안 이 서버 메모리에 모음", REDIS_SKIP_MILLIS, e);
    }

    private double sharedPendingUsers() {
        if (redisSkipped()) {
            return Double.NaN;
        }
        try {
            return pendingReads.countUsers();
        } catch (DataAccessException e) {
            return Double.NaN;
        }
    }
}