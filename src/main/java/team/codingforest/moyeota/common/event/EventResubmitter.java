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
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 미완료 outbox 이벤트를 백오프로 재발행하고, 상한을 넘은 건 dead letter 로 옮김 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventResubmitter {

    private static final int BATCH_SIZE = 200;

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
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${moyeota.event.resubmit.cron: 0 * * * * *}")
    @SchedulerLock(name = "event-resubmit", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
    public void run() {
        Timer.Sample sample = Timer.start(meterRegistry);
        int moved = 0;
        int discarded = 0;
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

            Instant now = Instant.now();
            publications.resubmitIncompletePublications(ResubmissionOptions.defaults()
                    .withMinAge(Duration.ofMinutes(1))
                    .withBatchSize(BATCH_SIZE)
                    .withFilter(publication -> shouldResubmit(publication, now)));
        } finally {
            long nanos = sample.stop(Timer.builder("event.resubmit.duration")
                    .description("outbox 이벤트 재발행 작업 1회 실행 시간")
                    .publishPercentiles(0.5, 0.99)
                    .register(meterRegistry));
            log.info("이벤트 재발행 실행 {}ms, dead letter {}건, 폐기 {}건",
                    nanos / 1_000_000, moved, discarded);
        }
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