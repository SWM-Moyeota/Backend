package team.codingforest.moyeota.user.sanction;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 *  V34 가 user_no_show 를 만들고 user_match_room 의 노쇼를 옮기는지 실제 Postgres 에서 본다.
 *  스키마 테스트는 컨텍스트가 뜰 때 최신 버전까지 올린 기본 스키마를 본다.
 *  migrate 테스트는 테스트마다 빈 스키마를 따로 만들어 V33 까지 올리고 노쇼를 넣은 뒤 V34 를 올린다 - dev 배포에서 V34 가 도는 순서다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class UserNoShowMigrationPostgresTest {
    private static final Instant MEETING_AT = Instant.parse("2026-11-01T09:00:00Z");

    @Autowired JdbcTemplate jdbc;
    @Value("${spring.datasource.url}") String url;
    @Value("${spring.datasource.username}") String username;
    @Value("${spring.datasource.password}") String password;

    // 기본 스키마와 섞이지 않게 테스트마다 따로 쓰는 스키마
    private final String scratch = "v34_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + scratch + " CASCADE");
    }

    @DisplayName("V34 - 기본 스키마에 user_no_show 가 열·유니크 제약·조회 인덱스와 함께 있고 FK 는 없다")
    @Test
    void v34_defaultSchema() {

        /*
         * settings
         */
        String schema = jdbc.queryForObject("SELECT current_schema()", String.class);

        /*
         * test
         */
        List<Map<String, Object>> columns = jdbc.queryForList("SELECT column_name, data_type, is_nullable FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = 'user_no_show' ORDER BY ordinal_position", schema);
        List<String> constraints = jdbc.queryForList("SELECT constraint_name || ':' || constraint_type FROM information_schema.table_constraints "
                + "WHERE table_schema = ? AND table_name = 'user_no_show'", String.class, schema);
        String index = jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE schemaname = ? AND indexname = 'idx_user_no_show_user_meeting_at'",
                String.class, schema);

        /*
         * verify
         */
        assertThat(columns).extracting(c -> c.get("column_name"), c -> c.get("data_type"), c -> c.get("is_nullable")).containsExactly(
                tuple("id", "bigint", "NO"),
                tuple("user_id", "bigint", "NO"),
                tuple("match_id", "bigint", "NO"),
                tuple("meeting_at", "timestamp with time zone", "NO"),
                tuple("voided_at", "timestamp with time zone", "YES"),
                tuple("created_at", "timestamp with time zone", "NO"));
        assertThat(constraints).contains("uk_user_no_show_user_match:UNIQUE")
                .anyMatch(c -> c.endsWith(":PRIMARY KEY"))
                .noneMatch(c -> c.endsWith(":FOREIGN KEY"));
        assertThat(index).contains("(user_id, meeting_at)");
    }

    @DisplayName("migrate - V33 상태의 노쇼를 무효로 돌린 것까지 옮기고, 노쇼가 아닌 멤버 행은 옮기지 않는다")
    @Test
    void migrate_backfillsNoShows() {

        /*
         * settings
         */
        migrateScratchTo("33");
        Instant noShowAt = MEETING_AT.plus(Duration.ofMinutes(40));
        Instant voidedAt = MEETING_AT.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.MICROS);
        Long room = reservedRoom(MEETING_AT);
        Long otherRoom = reservedRoom(MEETING_AT.plus(Duration.ofDays(3)));
        member(room, 1L, null, null, null);                          // 출석 기록 없음
        member(room, 2L, MEETING_AT.plusSeconds(60), null, null);    // 도착
        member(room, 3L, null, noShowAt, null);                      // 노쇼
        member(room, 4L, null, noShowAt, voidedAt);                  // 노쇼 → 무효
        member(otherRoom, 3L, null, noShowAt.plus(Duration.ofDays(3)), null);

        /*
         * test
         */
        migrateScratchTo("34");

        /*
         * verify
         */
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT user_id, match_id, meeting_at, voided_at, created_at FROM " + scratch
                + ".user_no_show ORDER BY match_id, user_id");
        assertThat(rows).extracting(r -> r.get("user_id"), r -> r.get("match_id"), r -> instant(r.get("meeting_at")),
                r -> instant(r.get("voided_at")), r -> instant(r.get("created_at"))).containsExactly(
                tuple(3L, room, MEETING_AT, null, noShowAt),
                tuple(4L, room, MEETING_AT, voidedAt, noShowAt),
                tuple(3L, otherRoom, MEETING_AT.plus(Duration.ofDays(3)), null, noShowAt.plus(Duration.ofDays(3))));
        assertThat(lastSuccessVersion()).isEqualTo("34");
    }

    @DisplayName("migrate - 노쇼가 없으면 빈 user_no_show 를 만든다")
    @Test
    void migrate_withoutNoShows() {

        /*
         * settings
         */
        migrateScratchTo("33");
        member(reservedRoom(MEETING_AT), 1L, null, null, null);

        /*
         * test
         */
        migrateScratchTo("34");

        /*
         * verify
         */
        assertThat(jdbc.queryForObject("SELECT count(*) FROM " + scratch + ".user_no_show", Integer.class)).isZero();
        assertThat(lastSuccessVersion()).isEqualTo("34");
    }

    private Long reservedRoom(Instant departureAt) {
        return jdbc.queryForObject("INSERT INTO " + scratch + ".match_room (capacity, departure_lat, departure_lng, destination_lat, destination_lng, "
                + "created_at, departure_place, destination_place, status, room_type, departure_at) "
                + "VALUES (3, 37.4979, 127.0276, 37.3947, 127.1112, now(), '강남역', '판교역', 'FINISHED', 'RESERVED', ?) RETURNING id",
                Long.class, Timestamp.from(departureAt));
    }

    private void member(Long room, Long userId, Instant arrivedAt, Instant noShowAt, Instant voidedAt) {
        jdbc.update("INSERT INTO " + scratch + ".user_match_room (match_id, user_id, created_at, arrived_at, no_show_at, no_show_voided_at) "
                + "VALUES (?, ?, now(), ?, ?, ?)", room, userId, timestamp(arrivedAt), timestamp(noShowAt), timestamp(voidedAt));
    }

    private void migrateScratchTo(String target) {
        // 앱의 커넥션 풀이 아닌 Flyway 전용 커넥션으로 돌려 풀 커넥션의 search_path 를 바꾸지 않는다
        Flyway.configure()
                .dataSource(url, username, password)
                .schemas(scratch)
                .target(target)
                .load()
                .migrate();
    }

    private String lastSuccessVersion() {
        return jdbc.queryForObject("SELECT version FROM " + scratch + ".flyway_schema_history WHERE success AND version IS NOT NULL "
                + "ORDER BY installed_rank DESC LIMIT 1", String.class);
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Object value) {
        return value == null ? null : ((Timestamp) value).toInstant();
    }
}
