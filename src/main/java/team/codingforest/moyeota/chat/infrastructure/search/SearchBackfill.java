package team.codingforest.moyeota.chat.infrastructure.search;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.chat.infrastructure.ChatMessageJpaRepository;

@Component
@RequiredArgsConstructor
public class SearchBackfill {
    private final ChatMessageJpaRepository messages;
    private final SearchOutboxRepository outbox;

    /** 수정/삭제와 동일한 메시지 행 잠금을 사용해 오래된 스냅샷에 새 버전이 배정되는 것을 방지한다. */
    @Transactional
    public long enqueueBatch(long after) {
        var batch = messages.findBackfillBatch(after, Limit.of(200));
        batch.forEach(message -> outbox.save(SearchOutboxEntry.of(message.toDomain())));
        return batch.isEmpty() ? after : batch.getLast().getId();
    }
}
