package team.codingforest.moyeota.chat.infrastructure.search;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import team.codingforest.moyeota.chat.domain.search.MessageSearchQuery;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** 합성 데이터만 사용하는 선택 실행 벤치마크. HTTP API 전체 지연이 아닌 검색 저장소 호출을 비교한다. */
@Tag("search-benchmark")
class ChatSearchBenchmark {
    @Test void DB와_검색엔진의_지연과_결과를_비교한다() throws Exception {
        int rows = Integer.parseInt(System.getenv().getOrDefault("SEARCH_BENCH_ROWS", "100000"));
        if (rows < 1000 || rows > 2000000) throw new IllegalArgumentException("측정 데이터는 1000~2000000건이어야 합니다");
        String indexName = "chat-bench-" + UUID.randomUUID();
        URI url = URI.create(System.getenv().getOrDefault("SEARCH_TEST_ES_URL", "http://localhost:19200"));
        var props = new ChatSearchProperties(true, url, indexName, "", Duration.ofSeconds(2), Duration.ofSeconds(10), 50);
        var client = RestClient.builder().baseUrl(url.toString()).build();
        var index = new ElasticsearchMessageIndex(client, props);
        String jdbcUrl = System.getenv().getOrDefault("SEARCH_TEST_JDBC_URL", "jdbc:postgresql://localhost:15432/search_test");
        StringBuilder report = new StringBuilder("# 채팅 검색 합성 데이터 벤치마크\n\n");
        report.append("- 측정 시각: ").append(Instant.now()).append("\n- 전체 메시지: ").append(rows)
                .append("건, 방 20개에 균등 분산\n- 단일 클라이언트, 워밍업 5회 후 30회 측정\n")
                .append("- PostgreSQL JDBC 조회와 Elasticsearch HTTP 조회(강조 포함)의 클라이언트 경과시간\n")
                .append("- 인증·권한 조회·DB 재검증·응답 직렬화를 포함한 서비스 API 응답시간이 아님\n")
                .append("- PostgreSQL에는 기존과 같은 (room_id, id) 인덱스만 적용. pg_trgm 등 별도 튜닝과의 비교는 아님\n")
                .append("- 로컬 Docker 단일 노드, Elasticsearch heap 512MB, 동시 부하 없음\n\n")
                .append("| 검색어 | 결과 수 | DB 평균(ms) | DB p95(ms) | ES 평균(ms) | ES p95(ms) |\n")
                .append("|---|---:|---:|---:|---:|---:|\n");
        try (Connection connection = DriverManager.getConnection(jdbcUrl,
                System.getenv().getOrDefault("SEARCH_TEST_DB_USER", "search_test"),
                System.getenv().getOrDefault("SEARCH_TEST_DB_PASSWORD", "search_test"))) {
            index.ensureIndex();
            client.put().uri("/{index}/_settings", indexName).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("index", Map.of("refresh_interval", "-1", "number_of_replicas", 0))).retrieve().toBodilessEntity();
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE search_bench (id bigint primary key, room_id bigint not null, content varchar(1000), deleted boolean not null)");
                statement.execute("CREATE INDEX ON search_bench (room_id, id)");
                statement.execute("INSERT INTO search_bench SELECT i, ((i-1)%20)+1, CASE WHEN i%1009=0 THEN '분실물 보관소에서 가방을 찾았습니다' ELSE '강남역 삼번출구 앞에서 만나 함께 이동합니다' END, false FROM generate_series(1," + rows + ") AS i");
                statement.execute("ANALYZE search_bench");
            }
            for (int start = 1; start <= rows; start += 2000) {
                StringBuilder bulk = new StringBuilder();
                for (int id = start; id < start + 2000 && id <= rows; id++) {
                    String content = id % 1009 == 0 ? "분실물 보관소에서 가방을 찾았습니다" : "강남역 삼번출구 앞에서 만나 함께 이동합니다";
                    bulk.append("{\"index\":{\"_id\":\"").append(id).append("\"}}\n")
                            .append("{\"messageId\":").append(id).append(",\"roomId\":").append((id - 1) % 20 + 1)
                            .append(",\"senderId\":7,\"content\":\"").append(content)
                            .append("\",\"deleted\":false,\"sentAt\":\"2026-09-20T00:00:00Z\"}\n");
                }
                JsonNode response = client.post().uri("/{index}/_bulk", indexName)
                        .contentType(MediaType.parseMediaType("application/x-ndjson")).body(bulk.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).retrieve().body(JsonNode.class);
                assertThat(response).isNotNull();
                assertThat(response.path("errors").asBoolean()).isFalse();
            }
            client.post().uri("/{index}/_refresh", indexName).retrieve().toBodilessEntity();
            for (String keyword : List.of("출구", "분실물", "없는단어")) {
                var query = new MessageSearchQuery(1L, keyword, null, null, null, null, 30);
                List<Double> sqlTimes = new ArrayList<>(), esTimes = new ArrayList<>();
                List<Long> sqlIds = List.of();
                for (int iteration = 0; iteration < 35; iteration++) {
                    long begin = System.nanoTime();
                    sqlIds = new ArrayList<>();
                    try (PreparedStatement statement = connection.prepareStatement("SELECT id, content FROM search_bench WHERE room_id = 1 AND deleted = false AND content LIKE ? ORDER BY id DESC LIMIT 31")) {
                        statement.setString(1, "%" + keyword + "%");
                        try (ResultSet result = statement.executeQuery()) { while (result.next()) { sqlIds.add(result.getLong(1)); result.getString(2); } }
                    }
                    double sqlElapsed = (System.nanoTime() - begin) / 1_000_000.0;
                    begin = System.nanoTime();
                    var hits = index.search(query);
                    double esElapsed = (System.nanoTime() - begin) / 1_000_000.0;
                    assertThat(hits.stream().map(h -> h.id()).toList()).isEqualTo(sqlIds);
                    if (iteration >= 5) { sqlTimes.add(sqlElapsed); esTimes.add(esElapsed); }
                }
                report.append(String.format(Locale.ROOT, "| %s | %d | %.3f | %.3f | %.3f | %.3f |%n", keyword, sqlIds.size(), mean(sqlTimes), p95(sqlTimes), mean(esTimes), p95(esTimes)));
            }
            JsonNode stats = client.get().uri("/{index}/_stats/store", indexName).retrieve().body(JsonNode.class);
            report.append("\n- ES primary store bytes: ").append(stats.path("_all").path("primaries").path("store").path("size_in_bytes").asLong()).append("\n");
            Files.createDirectories(Path.of("build/reports"));
            Files.writeString(Path.of("build/reports/chat-search-benchmark.md"), report.toString());
        } finally {
            client.delete().uri("/{index}", indexName).retrieve().toBodilessEntity();
        }
    }
    private double mean(List<Double> values) { return values.stream().mapToDouble(Double::doubleValue).average().orElseThrow(); }
    private double p95(List<Double> values) { return values.stream().sorted().toList().get((int) Math.ceil(values.size() * .95) - 1); }
}
