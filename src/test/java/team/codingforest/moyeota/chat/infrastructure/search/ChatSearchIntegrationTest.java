package team.codingforest.moyeota.chat.infrastructure.search;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.chat.domain.ChatMessage;
import team.codingforest.moyeota.chat.domain.search.MessageSearchQuery;
import team.codingforest.moyeota.chat.infrastructure.*;
import java.net.URI;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Tag("search-integration")
@DataJpaTest(properties = {"chat.search.enabled=true", "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.default-schema=chat_search_it",
        "spring.jpa.properties.hibernate.default_schema=chat_search_it"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ChatMessageJpa.class, SearchBackfill.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatSearchIntegrationTest {
    @Autowired ChatMessageJpa messages;
    @Autowired ChatMessageJpaRepository repository;
    @Autowired SearchOutboxRepository outbox;
    @Autowired SearchBackfill backfill;
    @Autowired TransactionTemplate tx;
    private RestClient http;
    private ElasticsearchMessageIndex index;
    private ChatSearchProperties properties;
    private SearchOutboxWorker worker;
    private final Instant sentAt = Instant.parse("2026-09-20T00:00:00Z");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv().getOrDefault("SEARCH_TEST_JDBC_URL", "jdbc:postgresql://localhost:15432/search_test"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("SEARCH_TEST_DB_USER", "search_test"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("SEARCH_TEST_DB_PASSWORD", "search_test"));
    }

    @BeforeEach void setup() {
        outbox.deleteAll(); repository.deleteAll();
        URI url = URI.create(System.getenv().getOrDefault("SEARCH_TEST_ES_URL", "http://localhost:19200"));
        properties = new ChatSearchProperties(true, url, "chat-it-" + UUID.randomUUID(), "", Duration.ofSeconds(2), Duration.ofSeconds(3), 50);
        http = RestClient.builder().baseUrl(url.toString()).build();
        index = new ElasticsearchMessageIndex(http, properties);
        worker = new SearchOutboxWorker(outbox, index, properties);
    }

    @AfterEach void cleanup() {
        if (index != null) {
            index.ensureIndex();
            http.delete().uri("/{index}", properties.index()).retrieve().toBodilessEntity();
        }
        outbox.deleteAll(); repository.deleteAll();
    }

    @Test void 한국어_부분문자열과_강조를_지원하고_HTML은_이스케이프한다() {
        var message = messages.save(ChatMessage.text(1L, 7L, "<script>alert(1)</script> 강남역삼번출구에서 만나요", sentAt));
        worker.drain(); refresh();
        var hits = index.search(query("역삼번출구", 1L, null, null, null, null, 10));
        assertThat(hits).extracting(h -> h.id()).containsExactly(message.getId());
        assertThat(hits.getFirst().highlight()).contains("<mark>").doesNotContain("<script>");
        assertThat(outbox.count()).isZero();
    }

    @Test void 방_작성자_기간_커서가_모두_검색에_반영된다() {
        var first = messages.save(ChatMessage.text(1L, 7L, "출구 앞입니다", sentAt));
        var second = messages.save(ChatMessage.text(1L, 7L, "출구 도착", sentAt.plusSeconds(1)));
        messages.save(ChatMessage.text(2L, 7L, "출구 다른방", sentAt));
        messages.save(ChatMessage.text(1L, 8L, "출구 다른사람", sentAt));
        worker.drain(); refresh();
        assertThat(index.search(query("출구", 1L, 7L, sentAt, sentAt.plusSeconds(2), null, 10)))
                .extracting(h -> h.id()).containsExactly(second.getId(), first.getId());
        assertThat(index.search(query("출구", 1L, 7L, null, null, second.getId(), 10)))
                .extracting(h -> h.id()).containsExactly(first.getId());
        assertThat(index.search(query("출구", 1L, 7L, sentAt, sentAt.plusSeconds(1), null, 10)))
                .extracting(h -> h.id()).containsExactly(first.getId());
    }

    @Test void 삭제_뒤_오래된_생성이나_중복_이벤트가_와도_본문은_부활하지_않는다() {
        var message = messages.save(ChatMessage.text(1L, 7L, "출구 비밀", sentAt));
        var creation = outbox.findAll().getFirst();
        message.delete(sentAt.plusSeconds(1)); messages.save(message);
        var deletion = outbox.findAll().stream().max(Comparator.comparing(SearchOutboxEntry::getId)).orElseThrow();
        index.index(deletion); index.index(creation); index.index(deletion); refresh();
        assertThat(index.search(query("출구", 1L, null, null, null, null, 10))).isEmpty();
        String stored = http.get().uri("/{index}/_doc/{id}", properties.index(), message.getId()).retrieve().body(String.class);
        assertThat(stored).doesNotContain("비밀");
    }

    @Test void ES_장애중에도_메시지를_저장하고_복구후_재처리한다() {
        var message = messages.save(ChatMessage.text(1L, 7L, "출구 재처리", sentAt));
        var unavailable = new ElasticsearchMessageIndex(RestClient.builder().baseUrl("http://127.0.0.1:1").build(), properties);
        new SearchOutboxWorker(outbox, unavailable, properties).drain();
        assertThat(repository.existsById(message.getId())).isTrue();
        var pending = outbox.findAll().getFirst();
        assertThat(pending.getAttempts()).isEqualTo(1);
        outbox.postpone(pending.getId(), Instant.now().minusSeconds(1));
        worker.drain(); refresh();
        assertThat(outbox.count()).isZero();
        assertThat(index.search(query("재처리", 1L, null, null, null, null, 10))).hasSize(1);
    }

    @Test void Postgres에서도_롤백된_메시지와_이벤트는_남지_않는다() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            messages.save(ChatMessage.text(1L, 7L, "출구 롤백", sentAt));
            throw new IllegalStateException("커밋 전 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test void 초기색인으로_기존메시지를_복구하고_삭제상태도_보존한다() {
        var active = messages.save(ChatMessage.text(1L, 7L, "출구 기존메시지", sentAt));
        var deleted = messages.save(ChatMessage.text(1L, 7L, "출구 지움", sentAt));
        deleted.delete(sentAt.plusSeconds(1)); messages.save(deleted);
        outbox.deleteAll();
        assertThat(backfill.enqueueBatch(0)).isEqualTo(deleted.getId());
        worker.drain(); refresh();
        assertThat(index.search(query("출구", 1L, null, null, null, null, 10)))
                .extracting(h -> h.id()).containsExactly(active.getId());
    }

    @Test void 부분문자열의_글자순서를_지키고_빈색인도_조회된다() {
        assertThat(index.search(query("출구", 1L, null, null, null, null, 10))).isEmpty();
        messages.save(ChatMessage.text(1L, 7L, "강남역", sentAt));
        worker.drain(); refresh();
        assertThat(index.search(query("남강", 1L, null, null, null, null, 10))).isEmpty();
    }

    private void refresh() { http.post().uri("/{index}/_refresh", properties.index()).retrieve().toBodilessEntity(); }
    private MessageSearchQuery query(String keyword, Long room, Long sender, Instant from, Instant until, Long cursor, int size) {
        return new MessageSearchQuery(room, keyword, sender, from, until, cursor, size);
    }
}
