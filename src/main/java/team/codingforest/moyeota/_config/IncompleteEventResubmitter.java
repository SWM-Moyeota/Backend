package team.codingforest.moyeota._config;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class IncompleteEventResubmitter {

    private static final Duration MIN_AGE = Duration.ofMinutes(5);
    private static final int BATCH_SIZE = 200;

    private final IncompleteEventPublications publications;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void resubmit() {
        publications.resubmitIncompletePublications(
                ResubmissionOptions.defaults()
                        .withMinAge(MIN_AGE)
                        .withBatchSize(BATCH_SIZE)
                        .withFilter(p -> p.getCompletionAttempts() < 5)
        );
    }
}
