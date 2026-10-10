package team.codingforest.moyeota.common.event;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** 미완료 outbox 이벤트를 백오프로 재발행하고, 상한을 넘은 건 dead letter 로 옮김 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventResubmitter {

    private static final int BATCH_SIZE = 200;
    private static final int LOW_WATERMARK = 100;   // 실행기 큐에 이보다 많이 밀려 있으면 이번 회차는 재발행을 쉼
    private static final Duration ABANDONED_AFTER = Duration.ofMinutes(1);   // 큐가 비었는데 해당시간만큼 PUBLISHED 면 거절돼 버려진 것으로 봄
    /**
     * 알림 리스너. 늦게 보내면 의미가 없어서 재발행하지 않고 버림
     * 클라이언트가 재연결이나 SYNC 때 다시 조회해서 맞춤
     */
    static final List<String> NO_RESUBMIT_LISTENERS = List.of(
            "team.codingforest.moyeota.chat.message.presentation.ChatMessageBroadcaster.",
            "team.codingforest.moyeota.matching.sse.PartySseListener.",
            "team.codingforest.moyeota.chat.notification.application.ChatNotificationListener.");

    private final IncompleteEventPublications publications;
    private final EventDeadLetters deadLetters;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 5_000, initialDelay = 60_000)
    @SchedulerLock(name = "event-resubmit", lockAtMostFor = "PT30S", lockAtLeastFor = "PT4S")
    public void run() {
        Timer.Sample sample = Timer.start(meterRegistry);
        int moved = 0;
        int discarded = 0;
        int abandoned = 0;
        try {
            discarded = deadLetters.discardFailed(NO_RESUBMIT_LISTENERS);
            if (discarded > 0) {
                meterRegistry.counter("event.discarded").increment(discarded);
            }

            moved = deadLetters.moveExhausted(EventRetryPolicy.MAX_ATTEMPTS);
            if (moved > 0) {
                log.warn("재시도 상한을 넘은 이벤트 {}건을 dead letter로 옮김", moved);
                meterRegistry.counter("event.dead_letter").increment(moved);
            }

            int room = resubmitRoom();
            if (room > 0) {
                Instant now = Instant.now();
                abandoned = deadLetters.markAbandonedFailed(now.minus(ABANDONED_AFTER), room);
                if (abandoned > 0) {
                    meterRegistry.counter("event.abandoned").increment(abandoned);
                }

                AtomicInteger budget = new AtomicInteger(room);
                publications.resubmitIncompletePublications(ResubmissionOptions.defaults()
                        .withMinAge(Duration.ofMinutes(1))
                        .withBatchSize(BATCH_SIZE)
                        .withFilter(publication -> shouldResubmit(publication, now) && budget.getAndDecrement() > 0));
            }
        } finally {
            long nanos = sample.stop(Timer.builder("event.resubmit.duration")
                    .description("outbox 이벤트 재발행 작업 1회 실행 시간")
                    .publishPercentiles(0.5, 0.99)
                    .register(meterRegistry));
            log.info("이벤트 재발행 실행 {}ms, dead letter {}건, 폐기 {}건, 버려진 건 {}건",
                    nanos / 1_000_000, moved, discarded, abandoned);
        }
    }

    /**
     * 메모리에 일이 밀려 있으면 그게 먼저 빠져야 버려진 것만 남음
     * 남은 자리의 절반만 채워 새로 들어오는 이벤트 자리를 남김
     */
    private int resubmitRoom() {
        BlockingQueue<Runnable> queue = taskExecutor.getThreadPoolExecutor().getQueue();
        if (queue.size() > LOW_WATERMARK) {
            return 0;
        }
        return queue.remainingCapacity() / 2;
    }

    static boolean shouldResubmit(EventPublication publication, Instant now) {
        if (isSkipped(publication)) {
            return false;
        }
        return EventRetryPolicy.isDue(
                publication.getCompletionAttempts(),
                publication.getPublicationDate(),
                publication.getLastResubmissionDate(),
                now);
    }

    /**
     * 공개 API 인 EventPublication 에는 리스너 정보가 없어서 구현 타입에서 꺼냄
     *
     * @param publication 재발행 후보
     */
    private static boolean isSkipped(EventPublication publication) {
        if (!(publication instanceof TargetEventPublication target)) {
            return false;
        }
        String listenerId = target.getTargetIdentifier().getValue();
        return NO_RESUBMIT_LISTENERS.stream().anyMatch(listenerId::startsWith);
    }
}