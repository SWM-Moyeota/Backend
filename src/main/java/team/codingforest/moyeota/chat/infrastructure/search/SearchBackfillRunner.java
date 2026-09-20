package team.codingforest.moyeota.chat.infrastructure.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chat.search", name = "backfill", havingValue = "true")
public class SearchBackfillRunner implements ApplicationRunner {
    private final SearchBackfill backfill;

    @Override
    public void run(ApplicationArguments args) {
        long cursor = 0;
        while (true) {
            long next = backfill.enqueueBatch(cursor);
            if (next == cursor) break;
            cursor = next;
            log.info("채팅 검색 초기 색인 예약 lastMessageId={}", cursor);
        }
    }
}
