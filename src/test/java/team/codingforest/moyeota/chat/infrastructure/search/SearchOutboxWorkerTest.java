package team.codingforest.moyeota.chat.infrastructure.search;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import team.codingforest.moyeota.chat.domain.ChatMessage;
import java.net.URI;
import java.time.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SearchOutboxWorkerTest {
    @Test void 실패_이벤트는_남기고_다른_이벤트를_계속_처리한다() {
        var outbox = mock(SearchOutboxRepository.class);
        var index = mock(ElasticsearchMessageIndex.class);
        var first = mock(SearchOutboxEntry.class); when(first.getId()).thenReturn(1L);
        var next = mock(SearchOutboxEntry.class); when(next.getId()).thenReturn(2L);
        when(outbox.findByNextAttemptAtLessThanEqualOrderByIdAsc(any(), any(Limit.class))).thenReturn(List.of(first, next));
        doThrow(new IllegalStateException("연결 실패")).when(index).index(first);
        new SearchOutboxWorker(outbox, index, properties()).drain();
        verify(outbox).postpone(eq(1L), any(Instant.class));
        verify(outbox, never()).acknowledge(1L);
        verify(outbox).acknowledge(2L);
    }
    static ChatSearchProperties properties() {
        return new ChatSearchProperties(true, URI.create("http://localhost:19200"), "chat-test", "", Duration.ofSeconds(1), Duration.ofSeconds(2), 50);
    }
}
