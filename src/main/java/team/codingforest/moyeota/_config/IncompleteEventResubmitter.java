package team.codingforest.moyeota._config;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class IncompleteEventResubmitter {

    private static final Duration MIN_AGE = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 5;
    private static final int LOW_WATERMARK = 100;
    private static final Duration RESUBMIT_COOLDOWN = MIN_AGE;

    private final IncompleteEventPublications publications;
    private final ThreadPoolTaskExecutor taskExecutor;

    @SchedulerLock(name = "event-resubmit", lockAtMostFor = "PT30S", lockAtLeastFor = "PT4S")
    @Scheduled(fixedDelay = 5_000, initialDelay = 60_000)
    public void resubmit() {
        BlockingQueue<Runnable> queue = taskExecutor.getThreadPoolExecutor().getQueue();
        if (queue.size() > LOW_WATERMARK) return;                     // 메모리에 일이 남아 있다 - 그게 먼저 빠져야 버려진 것만 남는다

        int room = queue.remainingCapacity() / 2;                     // 절반만 채운다 - 새로 들어오는 이벤트 자리를 남긴다
        Instant now = Instant.now();
        Instant publishedBefore = now.minus(MIN_AGE);
        Instant resubmittedBefore = now.minus(RESUBMIT_COOLDOWN);
        AtomicInteger budget = new AtomicInteger(room);
        publications.resubmitIncompletePublications(p ->
                p.getPublicationDate().isBefore(publishedBefore)
                        && notRecentlyResubmitted(p, resubmittedBefore)
                        && p.getCompletionAttempts() < MAX_ATTEMPTS
                        && budget.getAndDecrement() > 0);
    }

    /**
     * 5초마다 돌기 때문에, 방금 다시 넣어 아직 큐에서 기다리거나 처리 중인 것을 또 집으면 안 된다.
     * 밀린 게 100건 이하일 때는 큐가 계속 "거의 비어" 보여서 같은 행을 5초마다 다시 넣게 되고,
     * 멀쩡히 처리될 이벤트가 20초 만에 시도 횟수를 다 써 버려 영영 재시도 대상에서 빠진다.
     */
    private static boolean notRecentlyResubmitted(EventPublication p, Instant resubmittedBefore) {
        Instant last = p.getLastResubmissionDate();
        return last == null || last.isBefore(resubmittedBefore);
    }
}
