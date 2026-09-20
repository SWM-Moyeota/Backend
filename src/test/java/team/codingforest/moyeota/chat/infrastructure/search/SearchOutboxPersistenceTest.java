package team.codingforest.moyeota.chat.infrastructure.search;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.chat.domain.ChatMessage;
import team.codingforest.moyeota.chat.infrastructure.*;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = "chat.search.enabled=true")
@Import({ChatMessageJpa.class, SearchBackfill.class})
class SearchOutboxPersistenceTest {
    @Autowired ChatMessageJpa messages;
    @Autowired ChatMessageJpaRepository repository;
    @Autowired SearchOutboxRepository outbox;
    @Autowired SearchBackfill backfill;
    @Autowired TransactionTemplate tx;

    @Test void 생성과_삭제는_단조증가_버전과_삭제_스냅샷으로_저장된다() {
        var message = messages.save(ChatMessage.text(1L, 7L, "비공개 원문", Instant.now()));
        message.delete(Instant.now()); messages.save(message);
        var events = outbox.findAll().stream().filter(e -> e.getMessageId().equals(message.getId())).toList();
        assertThat(events).hasSize(2);
        assertThat(events.get(1).getId()).isGreaterThan(events.getFirst().getId());
        assertThat(events.get(1).getContent()).isEmpty();
        assertThat(events.get(1).isDeleted()).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 메시지_트랜잭션_롤백시_색인_요청도_남지_않는다() {
        long beforeMessages = repository.count(), beforeEvents = outbox.count();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            messages.save(ChatMessage.text(1L, 7L, "롤백할 메시지", Instant.now()));
            throw new IllegalStateException("실패 재현");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isEqualTo(beforeMessages);
        assertThat(outbox.count()).isEqualTo(beforeEvents);
    }

    @Test void 초기_색인은_삭제된_메시지의_본문을_복원하지_않는다() {
        var message = messages.save(ChatMessage.text(1L, 7L, "삭제 대상", Instant.now()));
        message.delete(Instant.now()); messages.save(message);
        outbox.deleteAll();
        long cursor = backfill.enqueueBatch(0);
        assertThat(cursor).isGreaterThanOrEqualTo(message.getId());
        assertThat(outbox.findAll().stream().filter(e -> e.getMessageId().equals(message.getId())))
                .allSatisfy(e -> { assertThat(e.isDeleted()).isTrue(); assertThat(e.getContent()).isEmpty(); });
        assertThat(backfill.enqueueBatch(cursor)).isEqualTo(cursor);
    }
}
