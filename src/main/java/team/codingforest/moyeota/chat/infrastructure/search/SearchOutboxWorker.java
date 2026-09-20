package team.codingforest.moyeota.chat.infrastructure.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chat.search", name = "enabled", havingValue = "true")
public class SearchOutboxWorker {
    private final SearchOutboxRepository outbox;
    private final ElasticsearchMessageIndex index;
    private final ChatSearchProperties properties;

    /** HTTP 호출 동안 DB 트랜잭션을 유지하지 않는다. 중복 실행은 외부 버전으로 안전하게 처리한다. */
    @Scheduled(scheduler = "chatSearchScheduler", fixedDelayString = "${chat.search.poll-delay:1000}", initialDelayString = "${chat.search.initial-delay:5000}")
    public void drain() {
        for (SearchOutboxEntry entry : outbox.findByNextAttemptAtLessThanEqualOrderByIdAsc(Instant.now(), Limit.of(properties.batchSize()))) {
            try {
                index.index(entry);
                outbox.acknowledge(entry.getId());
            } catch (RuntimeException e) {
                long seconds = Math.min(300, 1L << Math.min(entry.getAttempts() + 1, 8));
                outbox.postpone(entry.getId(), Instant.now().plusSeconds(seconds));
                log.warn("채팅 검색 색인 재시도 eventId={} messageId={} attempt={}",
                        entry.getId(), entry.getMessageId(), entry.getAttempts() + 1);
            }
        }
    }
}
