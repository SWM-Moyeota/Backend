package team.codingforest.moyeota._config;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class IncompleteEventResubmitter {

    private static final Duration MIN_AGE = Duration.ofMinutes(5);
    private static final int BATCH_SIZE = 200;

    private final IncompleteEventPublications publications;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void resubmit() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(5));
        AtomicInteger budget = new AtomicInteger(200);
        publications.resubmitIncompletePublications(p ->
                p.getPublicationDate().isBefore(cutoff)
                        && p.getCompletionAttempts() < 5
                        && budget.getAndDecrement() > 0);
    }
}
