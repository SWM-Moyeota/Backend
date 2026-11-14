package team.codingforest.moyeota.socket.order;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Consumer;

/**
 * 새 채팅 메시지를 방마다 순번(seq) 순서로만 내보냄
 *
 * API 서버 여러 대가 각자 Redis 로 발행해서 도착 순서가 순번과 어긋날 수 있음
 * 모든 메시지가 지나는 여기서 방마다 줄을 세워 다시 맞춤
 *
 * 규칙
 * - 기다리던 순번이 오면 바로 보내고 뒤에 대기 중인 순번도 이어서 보냄
 * - 앞 순번보다 뒤 순번이 먼저 오면 뒤 순번을 대기시킴
 * - 빈 순번을 chat.order.max-wait 동안 기다려도 안 오면 포기하고 다음 순번부터 보냄
 * - 포기한 뒤 늦게 온 순번은 보내지 않음 - WS 흐름은 절대 뒤집히지 않고 빈 순번은 앱이 /after 로 채움
 *
 * 소켓 서버가 여러 대여도 모두 같은 Redis 흐름을 같은 규칙으로 줄 세우므로 서버끼리 맞출 필요가 없음
 *
 * 알려진 한계: 이 서버가 방을 처음 볼 때(재시작 직후·조용한 방 정리 직후)는 받은 첫 순번부터 시작함
 * 그 순간 앞 순번이 뒤늦게 오면 지나간 순번으로 보고 버림 - 앱이 빈 순번으로 채움
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSeqOrderer {

    private static final int MAX_WAITING_PER_ROOM = 1_000;   // 넘으면 기다리지 않고 포기함 - 메모리 상한

    private final TaskScheduler chatOrderScheduler;   // 필드 이름으로 chatOrderScheduler 빈을 받음 - heartbeatScheduler 와 구분
    private final ChatOrderProperties properties;
    private final ChatOrderMetrics metrics;
    private final Map<Long, RoomLine> lines = new ConcurrentHashMap<>();

    @PostConstruct
    void start() {
        metrics.watchRoomCount(lines);
        chatOrderScheduler.scheduleAtFixedRate(this::closeIdleLines, Duration.ofMinutes(1));
    }

    /**
     * 새 메시지를 방 줄에 넣고 보낼 차례가 된 메시지를 순번 순서로 보냄
     *
     * @param chatRoomId 메시지가 속한 채팅방 id
     * @param seq        방 안 메시지 순번
     * @param send       실제로 구독자에게 보내는 일 - 차례가 되면 한 번 실행됨
     */
    public void offer(Long chatRoomId, long seq, Runnable send) {
        inRoomLine(chatRoomId, line -> line.accept(seq, send));
    }

    /**
     * 방 줄을 꺼내 잠근 채로 일을 함
     * 받는 스레드와 타이머 스레드가 같은 줄을 건드리므로 한 번에 하나씩만 들어가게 함
     * 조용해서 막 정리된 줄을 잡았으면 새 줄로 다시 시도함
     */
    private void inRoomLine(Long chatRoomId, Consumer<RoomLine> work) {
        while (true) {
            RoomLine line = lines.computeIfAbsent(chatRoomId, id -> new RoomLine());
            synchronized (line) {
                if (line.closed) {
                    continue;
                }
                work.accept(line);
                return;
            }
        }
    }

    private void closeIdleLines() {
        long now = System.nanoTime();
        lines.forEach((chatRoomId, line) -> {
            synchronized (line) {
                if (line.isIdleSince(now)) {
                    line.closed = true;
                    lines.remove(chatRoomId, line);
                }
            }
        });
    }

    private record Waiting(Runnable send, long arrivedAt) {
    }

    /** 방 하나의 줄 - 모든 메서드는 이 줄을 잠근 상태에서만 불림 */
    private final class RoomLine {

        private static final long NOT_STARTED = -1;

        private final TreeMap<Long, Waiting> waiting = new TreeMap<>();   // 앞 순번을 기다리는 메시지, 순번 순
        private long expectedSeq = NOT_STARTED;                           // 다음에 보낼 순번
        private ScheduledFuture<?> giveUpTimer;
        private long giveUpTimerTarget;                                   // 타이머가 기다리는 순번
        private long lastActivityAt = System.nanoTime();
        private boolean closed;

        void accept(long seq, Runnable send) {
            lastActivityAt = System.nanoTime();

            if (expectedSeq == NOT_STARTED) {
                expectedSeq = seq;                 // 이 서버가 처음 보는 방 - 받은 첫 순번부터 시작
            }

            if (seq < expectedSeq) {
                metrics.lateDropped();             // 이미 지나간 순번 - 보내면 순서가 뒤집힘
                return;
            }

            if (seq == expectedSeq) {
                sendNow(send);
            } else {
                waitForEarlierSeq(seq, send);
            }

            sendWaitingInOrder();
            updateGiveUpTimer();
        }

        private void sendNow(Runnable send) {
            deliver(send);
            expectedSeq++;
        }

        private void waitForEarlierSeq(long seq, Runnable send) {
            waiting.put(seq, new Waiting(send, System.nanoTime()));
            metrics.held();

            if (waiting.size() > MAX_WAITING_PER_ROOM) {
                skipMissingSeq();                  // 너무 많이 쌓임 - 기다리지 않고 포기함
            }
        }

        /** 기다리던 순번부터 이어지는 만큼 대기 중인 메시지를 보냄 */
        private void sendWaitingInOrder() {
            Waiting next;
            while ((next = waiting.remove(expectedSeq)) != null) {
                metrics.waited(System.nanoTime() - next.arrivedAt());
                sendNow(next.send());
            }
        }

        /** 빈 순번을 포기하고 대기 중인 가장 앞 순번으로 건너뜀 */
        private void skipMissingSeq() {
            long firstWaiting = waiting.firstKey();
            metrics.skipped(firstWaiting - expectedSeq);
            expectedSeq = firstWaiting;
        }

        /**
         * 대기 중인 메시지가 없으면 타이머를 끄고
         * 이미 같은 빈 순번을 기다리는 중이면 그대로 두고
         * 새 빈 순번이 생겼으면 그 순번을 chat.order.max-wait 동안 기다림
         */
        private void updateGiveUpTimer() {
            if (waiting.isEmpty()) {
                cancelGiveUpTimer();
                return;
            }
            if (giveUpTimer != null && giveUpTimerTarget == expectedSeq) {
                return;
            }
            cancelGiveUpTimer();
            long target = expectedSeq;
            giveUpTimerTarget = target;
            Instant giveUpAt = Instant.now().plus(properties.maxWait());
            giveUpTimer = chatOrderScheduler.schedule(() -> giveUpWaitingFor(target), giveUpAt);
        }

        private void cancelGiveUpTimer() {
            if (giveUpTimer != null) {
                giveUpTimer.cancel(false);
                giveUpTimer = null;
            }
        }

        /** 타이머 스레드에서 불림 - target 순번이 아직 안 왔으면 포기하고 다음 순번부터 보냄 */
        private void giveUpWaitingFor(long target) {
            synchronized (this) {
                if (closed || expectedSeq != target || waiting.isEmpty()) {
                    return;                        // 그사이 빈 순번이 채워져 이미 지나간 타이머
                }
                giveUpTimer = null;
                skipMissingSeq();
                sendWaitingInOrder();
                updateGiveUpTimer();
            }
        }

        boolean isIdleSince(long now) {
            return waiting.isEmpty() && now - lastActivityAt > properties.idleEviction().toNanos();
        }

        private void deliver(Runnable send) {
            try {
                send.run();
            } catch (Exception e) {
                log.error("순서 맞춘 메시지 전달 실패", e);   // 한 건이 실패해도 뒤 순번은 계속 보냄
            }
        }
    }
}
