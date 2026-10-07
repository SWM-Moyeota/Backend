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

    private static final Duration MIN_AGE = Duration.ofSeconds(30);   // 방금 발행돼 아직 큐에 들어가는 중인 것은 건드리지 않는다
    private static final int MAX_ATTEMPTS = 5;
    private static final int LOW_WATERMARK = 100;                     // 큐에 이만큼 이하로 남았을 때만 채운다
    // 방금 다시 넣은 것은 처리될 시간을 준다. MIN_AGE 보다 길게 잡으면 안 된다 - Modulith 는 처음 저장할 때
    // 마지막 재제출 시각을 발행 시각으로 채우므로, 이 값이 더 길면 한 번도 다시 넣지 않은 이벤트까지 이만큼 기다리게 된다
    private static final Duration RESUBMIT_COOLDOWN = MIN_AGE;

    private final IncompleteEventPublications publications;
    private final ThreadPoolTaskExecutor taskExecutor;                // 리스너가 도는 그 실행기

    // 서버가 여러 대면 한 대만 돈다 - 둘이 같은 행을 집으면 같은 이벤트가 두 번 처리된다.
    // lockAtMostFor: 돌던 서버가 죽어도 이 시간 뒤엔 다른 서버가 이어받는다. lockAtLeastFor: 두 대가 번갈아 돌아 주기가 절반이 되지 않게
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
