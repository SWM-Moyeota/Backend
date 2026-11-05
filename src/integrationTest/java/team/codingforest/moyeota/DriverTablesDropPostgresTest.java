package team.codingforest.moyeota;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 *  V32 가 기사·택시·정산·지급 테이블 8개를 지우는지 실제 Postgres 에서 본다.
 *  V32 테스트는 컨텍스트가 뜰 때 V1..V32 를 올린 기본 스키마를 본다.
 *  migrate 테스트는 테스트마다 빈 스키마를 따로 만들어 prod·dev 가 지금 있는 버전까지 올린 뒤 이어서 올린다 - 실제 배포에서 V32 가 도는 순서다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class DriverTablesDropPostgresTest {
    private static final String DRIVER_PAYOUT_ACCOUNT = "driver_payout_account";
    private static final List<String> DROPPED = List.of("taxi", "taxi_driver", "driver_setting", "driver_report",
            "settlement", "settlement_ledger", "payout", DRIVER_PAYOUT_ACCOUNT);
    // 살아 있는 테이블 - V32 가 건드리면 안 된다
    // 코드가 @Table 로 매핑하는 14개 + 엔티티 없이 JDBC 로 쓰는 2개(Spring Modulith event_publication, EventDeadLetterJdbc event_dead_letter)
    private static final List<String> LIVE = List.of("chat_message", "chat_report", "chat_room", "chat_room_user", "favorite_place",
            "local_user", "match_room", "no_show_objection", "refresh_token", "ride_record", "user_deletion", "user_match_room",
            "user_profile", "users", "event_publication", "event_dead_letter");

    @Autowired JdbcTemplate jdbc;
    @Value("${spring.datasource.url}") String url;
    @Value("${spring.datasource.username}") String username;
    @Value("${spring.datasource.password}") String password;

    // 기본 스키마와 섞이지 않게 테스트마다 따로 쓰는 스키마
    private final String scratch = "v32_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + scratch + " CASCADE");
    }

    @DisplayName("V32 - 기본 스키마에 지운 8개 테이블이 없고, 살아 있는 테이블은 그대로 있다")
    @Test
    void v32_defaultSchema() {

        /*
         * test
         */
        String schema = jdbc.queryForObject("SELECT current_schema()", String.class);
        List<String> tables = tablesIn(schema);

        /*
         * verify
         */
        assertThat(tables).doesNotContainAnyElementsOf(DROPPED);
        assertThat(tables).containsAll(LIVE);
    }

    @DisplayName("V32 - 기본 스키마에 성공으로 기록된다")
    @Test
    void v32_recordedAsSuccess() {

        /*
         * test
         */
        Boolean success = jdbc.queryForObject("SELECT success FROM flyway_schema_history WHERE version = '32'", Boolean.class);

        /*
         * verify
         */
        assertThat(success).isTrue();
    }

    @DisplayName("migrate - prod 상태(V22)에서 V23~V32 를 한 번에 올리면 V29 가 만든 driver_payout_account 까지 8개가 모두 지워진다")
    @Test
    void migrate_fromProdState() {

        /*
         * settings
         */
        migrateScratchTo("22");
        assertThat(tablesIn(scratch)).containsAll(DROPPED.stream().filter(table -> !table.equals(DRIVER_PAYOUT_ACCOUNT)).toList())
                .doesNotContain(DRIVER_PAYOUT_ACCOUNT);

        /*
         * test
         */
        migrateScratchTo("latest");

        /*
         * verify
         */
        assertThat(tablesIn(scratch)).doesNotContainAnyElementsOf(DROPPED).containsAll(LIVE);
        assertThat(lastSuccessVersion()).isEqualTo("32");
    }

    @DisplayName("migrate - dev 상태(V30)에서 V31·V32 를 올리면 8개가 모두 지워진다")
    @Test
    void migrate_fromDevState() {

        /*
         * settings
         */
        migrateScratchTo("30");
        assertThat(tablesIn(scratch)).containsAll(DROPPED);

        /*
         * test
         */
        migrateScratchTo("latest");

        /*
         * verify
         */
        assertThat(tablesIn(scratch)).doesNotContainAnyElementsOf(DROPPED).containsAll(LIVE);
        assertThat(lastSuccessVersion()).isEqualTo("32");
    }

    @DisplayName("migrate - driver_payout_account 가 없는 DB 에서도 IF EXISTS 라 V32 가 성공한다")
    @Test
    void migrate_withoutDriverPayoutAccount() {

        /*
         * settings
         */
        migrateScratchTo("31");
        // 기본 스키마는 컨텍스트가 이미 V32 까지 올려 driver_payout_account 가 없다 - search_path 를 타고 다른 스키마의 테이블을 지울 일은 없다
        jdbc.execute("DROP TABLE " + scratch + "." + DRIVER_PAYOUT_ACCOUNT);

        /*
         * test
         */
        migrateScratchTo("latest");

        /*
         * verify
         */
        assertThat(tablesIn(scratch)).doesNotContainAnyElementsOf(DROPPED).containsAll(LIVE);
        assertThat(lastSuccessVersion()).isEqualTo("32");
    }

    @DisplayName("migrate - prod 상태(V22)에서 7개를 손으로 먼저 지우면 IF EXISTS 가 없는 V23 이 실패해 배포가 멈춘다")
    @Test
    void migrate_failsWhenDroppedByHandBeforeV23() {

        /*
         * settings
         */
        migrateScratchTo("22");
        // 서로 가리키는 FK 가 있어도 한 문장으로 같이 지우면 순서와 상관없이 지워진다
        jdbc.execute("DROP TABLE " + String.join(", ", DROPPED.stream()
                .filter(table -> !table.equals(DRIVER_PAYOUT_ACCOUNT))
                .map(table -> scratch + "." + table)
                .toList()));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> migrateScratchTo("latest"));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(FlywayException.class).hasMessageContaining("V23");
        assertThat(thrown).rootCause().isInstanceOfSatisfying(SQLException.class,
                e -> assertThat(e.getSQLState()).isEqualTo("42P01"));   // undefined_table
        assertThat(lastSuccessVersion()).isEqualTo("22");
    }

    @DisplayName("migrate - 지울 테이블에 기대는 뷰가 있으면 CASCADE 가 없어 실패하고, 8개 중 하나도 지워지지 않는다")
    @Test
    void migrate_failsOnUnexpectedDependency() {

        /*
         * settings
         */
        migrateScratchTo("31");
        jdbc.execute("CREATE VIEW " + scratch + ".taxi_plate AS SELECT plate_number FROM " + scratch + ".taxi");

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> migrateScratchTo("latest"));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(FlywayException.class);
        assertThat(thrown).rootCause().isInstanceOfSatisfying(SQLException.class,
                e -> assertThat(e.getSQLState()).isEqualTo("2BP01"));   // dependent_objects_still_exist
        assertThat(tablesIn(scratch)).containsAll(DROPPED).contains("taxi_plate");
        assertThat(lastSuccessVersion()).isEqualTo("31");
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

    private List<String> tablesIn(String schema) {
        return jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = ?", String.class, schema);
    }

    private String lastSuccessVersion() {
        return jdbc.queryForObject("SELECT version FROM " + scratch + ".flyway_schema_history WHERE success AND version IS NOT NULL "
                + "ORDER BY installed_rank DESC LIMIT 1", String.class);
    }
}
