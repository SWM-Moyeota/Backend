package team.codingforest.moyeota.chat.infrastructure.search;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import team.codingforest.moyeota.chat.domain.search.*;
import team.codingforest.moyeota.chat.domain.exception.ChatException;
import tools.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static team.codingforest.moyeota.chat.domain.exception.ChatErrorCode.CHAT_SEARCH_UNAVAILABLE;

/** 공식 REST API 사용. 쿼리 문자열을 DSL에 보간하지 않고 JSON 값으로 전달한다. */
public class ElasticsearchMessageIndex implements MessageSearchIndex {
    private final RestClient client;
    private final ChatSearchProperties properties;
    private volatile boolean initialized;

    public ElasticsearchMessageIndex(RestClient client, ChatSearchProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public synchronized void ensureIndex() {
        if (initialized) return;
        try {
            String mapping = new ClassPathResource("elasticsearch/chat-messages-v1.json")
                    .getContentAsString(StandardCharsets.UTF_8);
            client.put().uri("/{index}", properties.index()).contentType(MediaType.APPLICATION_JSON)
                    .body(mapping).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() != 400 || !e.getResponseBodyAsString().contains("resource_already_exists_exception")) throw e;
        } catch (IOException e) {
            throw new IllegalStateException("검색 인덱스 정의를 읽을 수 없습니다", e);
        }
        initialized = true;
    }

    public void index(SearchOutboxEntry entry) {
        ensureIndex();
        try {
            // 삭제도 빈 본문의 문서로 남겨 오래된 생성 이벤트가 메시지를 부활시키지 못하게 한다.
            client.put().uri("/{index}/_doc/{id}?version={version}&version_type=external_gte",
                            properties.index(), entry.getMessageId(), entry.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("messageId", entry.getMessageId(), "roomId", entry.getRoomId(),
                            "senderId", entry.getSenderId(), "content", entry.getContent(),
                            "deleted", entry.isDeleted(), "sentAt", entry.getSentAt().toString()))
                    .retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) initialized = false;
            if (e.getStatusCode().value() != 409) throw e; // 이미 더 최신 버전이 반영된 이벤트는 완료 처리
        }
    }

    @Override
    public List<Hit> search(MessageSearchQuery query) {
        if (!properties.enabled()) throw new ChatException(CHAT_SEARCH_UNAVAILABLE);
        try {
            ensureIndex();
            JsonNode response = client.post().uri("/{index}/_search", properties.index())
                    .contentType(MediaType.APPLICATION_JSON).body(searchBody(query)).retrieve().body(JsonNode.class);
            if (response == null || response.path("timed_out").asBoolean()
                    || response.path("_shards").path("failed").asInt() > 0 || !response.path("hits").path("hits").isArray())
                throw new IllegalStateException("불완전한 검색 응답");
            List<Hit> hits = new ArrayList<>();
            for (JsonNode hit : response.path("hits").path("hits")) {
                hits.add(new Hit(Long.parseLong(hit.path("_id").asText()),
                        hit.path("_source").path("content").asText(),
                        hit.path("highlight").path("content").path(0).asText("")));
            }
            return hits;
        } catch (RuntimeException e) {
            // 외부 오류 본문에는 검색어/메시지가 포함될 수 있어 응답과 로그에 노출하지 않는다.
            throw new ChatException(CHAT_SEARCH_UNAVAILABLE);
        }
    }

    Map<String, Object> searchBody(MessageSearchQuery q) {
        List<Object> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("roomId", q.roomId())));
        filters.add(Map.of("term", Map.of("deleted", false)));
        if (q.senderId() != null) filters.add(Map.of("term", Map.of("senderId", q.senderId())));
        if (q.cursor() != null) filters.add(Map.of("range", Map.of("messageId", Map.of("lt", q.cursor()))));
        Map<String, Object> dates = new LinkedHashMap<>();
        if (q.from() != null) dates.put("gte", q.from().toString());
        if (q.until() != null) dates.put("lt", q.until().toString());
        if (!dates.isEmpty()) filters.add(Map.of("range", Map.of("sentAt", dates)));
        return Map.of("size", q.size() + 1, "track_total_hits", false,
                "_source", List.of("content"), "sort", List.of(Map.of("messageId", "desc")),
                "query", Map.of("bool", Map.of("filter", filters, "must", List.of(Map.of("multi_match", Map.of(
                        "query", q.keyword(), "type", "phrase", "fields", List.of("content", "content.partial")))))),
                "highlight", Map.of("encoder", "html", "pre_tags", List.of("<mark>"), "post_tags", List.of("</mark>"),
                        "fields", Map.of("content", Map.of("matched_fields", List.of("content.partial"),
                                "fragment_size", 150, "number_of_fragments", 1))));
    }
}
