package team.codingforest.moyeota;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  V37 이 노쇼 테이블·컬럼을 지우고, 약속 없이 COMPLETED 가 된 방을 모집 중(ACTIVE)으로 되돌리는지 실제 Postgres 에서 본다.
 *  스키마 테스트는 컨텍스트가 뜰 때 최신 버전까지 올린 기본 스키마를 보고,
 *  되돌리기 테스트는 빈 스키마를 따로 만들어 V36 까지 올린 뒤 행을 넣고 V37 을 올린다 - 실제 배포에서 V37 이 도는 순서다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class NoShowDropMigrationPostgresTest {
    private static final List<String> DROPPED_TABLES = List.of("user_no_show", "no_show_objection");

    @Autowired JdbcTemplate jdbc;
    @Value("${spring.datasource.url}") String url;
    @Value("${spring.datasource.username}") String username;
    @Value("${spring.datasource.password}") String password;

    // 기본 스키마와 섞이지 않게 테스트마다 따로 쓰는 스키마
    private final String scratch = "v37_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + scratch + " CASCADE");
    }

    @DisplayName("V37 - 기본 스키마에 user_no_show·no_show_objection 테이블이 없다")
    @Test
    void v37_noShowTablesDropped() {

        /*
         * test
         */
        String schema = jdbc.queryForObject("SELECT current_schema()", String.class);
        List<String> tables = jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = ?", String.class, schema);

        /*
         * verify
         */
        assertThat(tables).doesNotContainAnyElementsOf(DROPPED_TABLES);
        assertThat(tables).as("살아 있는 테이블은 그대로다").contains("users", "user_match_room", "match_room");
    }

    @DisplayName("V37 - users 의 노쇼 횟수·제한 컬럼과 user_match_room 의 노쇼 컬럼이 없고 도착 컬럼은 남는다")
    @Test
    void v37_noShowColumnsDropped() {

        /*
         * test
         */
        List<String> userColumns = columnsOf("users");
        List<String> memberColumns = columnsOf("user_match_room");

        /*
         * verify
         */
        assertThat(userColumns).doesNotContain("restricted_until", "no_show_count", "no_show_count_updated_at");
        assertThat(memberColumns).doesNotContain("no_show_at", "no_show_voided_at");
        assertThat(memberColumns).as("출발지 도착 시각은 탑승자 판정에 계속 쓴다").contains("arrived_at");
    }

    @DisplayName("V37 - 기본 스키마에 성공으로 기록된다")
    @Test
    void v37_recordedAsSuccess() {

        /*
         * test
         */
        Boolean success = jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '37'", Boolean.class);

        /*
         * verify
         */
        assertThat(success).isTrue();
    }

    @DisplayName("migrate - V36 상태의 COMPLETED 방 중 약속이 없는 실시간 방과 예약 방은 ACTIVE 로 돌아가고, 약속을 확정한 실시간 방과 닫힌 방은 그대로다")
    @Test
    void migrate_resetsUnconfirmedCompleted() {

        /*
         * settings
         */
        migrateScratchTo("36");
        Long 미확정실시간 = room("COMPLETED", "REALTIME", null);
        Long 확정실시간 = room("COMPLETED", "REALTIME", "now() + interval '30 minutes'");
        Long 예약 = room("COMPLETED", "RESERVED", "now() + interval '1 day'");
        Long 끝난실시간 = room("FINISHED", "REALTIME", null);
        Long 취소된예약 = room("CANCELED", "RESERVED", "now() + interval '1 day'");
        Long 모집중 = room("ACTIVE", "REALTIME", null);

        /*
         * test
         */
        migrateScratchTo("37");

        /*
         * verify
         */
        assertThat(statusOf(미확정실시간)).isEqualTo("ACTIVE");
        assertThat(statusOf(예약)).isEqualTo("ACTIVE");
        assertThat(statusOf(확정실시간)).as("약속을 확정한 실시간 방만 COMPLETED 로 남는다").isEqualTo("COMPLETED");
        assertThat(statusOf(끝난실시간)).isEqualTo("FINISHED");
        assertThat(statusOf(취소된예약)).isEqualTo("CANCELED");
        assertThat(statusOf(모집중)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = ?", String.class, scratch))
                .doesNotContainAnyElementsOf(DROPPED_TABLES);
    }

    private List<String> columnsOf(String table) {
        String schema = jdbc.queryForObject("SELECT current_schema()", String.class);
        return jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?",
                String.class, schema, table);
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

    /** scratch 스키마의 방. 실시간 방은 경로·요금·시간이 있고 예약 방은 출발 시각이 있어야 V24 CHECK 를 지난다 */
    private Long room(String status, String roomType, String departureAtSql) {
        boolean realtime = roomType.equals("REALTIME");
        return jdbc.queryForObject("""
                INSERT INTO %s.match_room (capacity, departure_lat, departure_lng, destination_lat, destination_lng,
                                           created_at, departure_place, destination_place, status, version, room_type,
                                           estimated_fare, estimated_time, route, departure_at)
                VALUES (3, 35.1595, 126.8526, 35.1768, 126.9055, now(), '광주송정역', '전남대', ?, 0, ?, %s, %s, %s, %s)
                RETURNING id
                """.formatted(scratch,
                        realtime ? "9000" : "NULL", realtime ? "20" : "NULL", realtime ? "'x'" : "NULL",
                        departureAtSql == null ? "NULL" : departureAtSql),
                Long.class, status, roomType);
    }

    private String statusOf(Long id) {
        return jdbc.queryForObject("SELECT status FROM " + scratch + ".match_room WHERE id = ?", String.class, id);
    }
}
