package team.codingforest.moyeota.chat.room.application;

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
import team.codingforest.moyeota.chat.room.domain.ChatEventDeadLetters;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventResubmitter {

    static final String TARGET_LISTENER = ChatRoomMatchingListener.class.getName() + ".";

    private final IncompleteEventPublications publications;
    private final ChatEventDeadLetters deadLetters;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${moyeota.chat.resubmit.cron: 0 * * * * *}")
    @SchedulerLock(name = "chat-event-resubmit", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
    public void run() {
        Timer.Sample sample = Timer.start(meterRegistry);
        int moved = 0;
        try {
            moved = deadLetters.moveExhausted(TARGET_LISTENER, ChatEventRetryPolicy.MAX_ATTEMPTS);
            if (moved > 0) {
                log.warn("재시도 상한을 넘은 채팅 이벤트 {}건을 dead letter로 옮김", moved);
                meterRegistry.counter("chat.event.dead_letter").increment(moved);
            }

            Instant now = Instant.now();
            publications.resubmitIncompletePublications(ResubmissionOptions.defaults()
                    .withMinAge(Duration.ofMinutes(1))
                    .withFilter(publication -> shouldResubmit(publication, now)));
        } finally {
            long nanos = sample.stop(Timer.builder("chat.event.resubmit.duration")
                    .description("채팅 이벤트 재발행 작업 1회 실행 시간")
                    .publishPercentiles(0.5, 0.99)
                    .register(meterRegistry));
            log.info("채팅 이벤트 재발행 실행 {}ms, dead letter {}건", nanos / 1_000_000, moved);
        }

    }

    static boolean shouldResubmit(EventPublication publication, Instant now) {
        return publication instanceof TargetEventPublication target
                && target.getTargetIdentifier().getValue().startsWith(TARGET_LISTENER)
                && ChatEventRetryPolicy.isDue(
                        publication.getCompletionAttempts(),
                        publication.getPublicationDate(),
                        publication.getLastResubmissionDate(),
                        now);
    }
}
