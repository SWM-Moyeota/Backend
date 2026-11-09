package team.codingforest.moyeota.matching.party.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.RoomType;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  match_room 에 남았지만 코드가 매핑하지 않는 completed_at·departure_radius·destination_radius 를 실제 Postgres 에서 본다.
 *  반경 두 컬럼은 NOT NULL 이라 V31 기본값(500)이 없으면 매핑이 빠진 INSERT 가 실패한다.
 *  배포 중에는 구버전 서버가 두 컬럼과 completed_at 을 직접 쓰므로, 그런 행도 새 코드가 읽고 고쳐 저장할 수 있어야 한다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class PartyUnmappedColumnPostgresTest {
    // 다른 테스트가 남긴 방과 섞이지 않게 광주 좌표를 쓴다
    private static final Location DEPARTURE = new Location(35.1595, 126.8526);
    private static final Location DESTINATION = new Location(35.1768, 126.9055);
    private static final String RADIUS_AND_COMPLETED_AT = "SELECT departure_radius, destination_radius, completed_at FROM match_room WHERE id = ?";

    @Autowired Parties parties;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private final Long 방장 = ThreadLocalRandom.current().nextLong(8_000_000_000L, 9_000_000_000L);
    private final Long 동승자 = 방장 + 1;
    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        created.forEach(id -> {
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match_room WHERE id = ?", id);
        });
    }

    @DisplayName("V31 - departure_radius·destination_radius 는 NOT NULL 그대로이고 기본값이 500 이다")
    @Test
    void v31_radiusDefault() {

        /*
         * test
         */
        List<Map<String, Object>> columns = jdbc.queryForList("""
                SELECT column_name, is_nullable, column_default FROM information_schema.columns
                WHERE table_name = 'match_room' AND column_name IN ('departure_radius', 'destination_radius')
                ORDER BY column_name
                """);

        /*
         * verify
         */
        assertThat(columns).hasSize(2);
        assertThat(columns).allSatisfy(column -> assertThat(column)
                .containsEntry("is_nullable", "NO")
                .containsEntry("column_default", "500"));
    }

    @DisplayName("V31 - 반경 두 컬럼을 빼고 INSERT 하면 500 으로 들어간다")
    @Test
    void v31_insertWithoutRadius() {

        /*
         * test
         */
        Map<String, Object> row = jdbc.queryForMap("""
                INSERT INTO match_room (capacity, departure_lat, departure_lng, destination_lat, destination_lng,
                                        created_at, departure_place, destination_place, status, version, room_type, estimated_fare, estimated_time, route)
                VALUES (3, 35.1595, 126.8526, 35.1768, 126.9055, now(), '광주송정역', '전남대', 'ACTIVE', 0, 'REALTIME', 9000, 20, 'x')
                RETURNING id, departure_radius, destination_radius
                """);
        created.add(((Number) row.get("id")).longValue());

        /*
         * verify
         */
        assertThat(row).containsEntry("departure_radius", 500).containsEntry("destination_radius", 500);
    }

    @DisplayName("save - 새 실시간 방은 반경·completed_at 없이 저장되고(반경 500, completed_at NULL) 정원이 차도 completed_at 을 쓰지 않는다")
    @Test
    void save_realtimeWithoutUnmappedColumns() {

        /*
         * settings
         */
        Long id = save(Party.create(방장, DEPARTURE, DESTINATION, "광주송정역", "전남대", new Capacity(2), Instant.now(),
                9000, 20, "_p~iF~ps|U_ulLnnqC"));

        /*
         * test
         */
        tx.executeWithoutResult(status -> {
            Party locked = parties.findByIdForUpdate(id).orElseThrow();
            locked.join(동승자);   // 정원 2 충족 → COMPLETED
            parties.save(locked);
        });

        /*
         * verify
         */
        Map<String, Object> row = jdbc.queryForMap(RADIUS_AND_COMPLETED_AT, id);
        assertThat(row).containsEntry("departure_radius", 500).containsEntry("destination_radius", 500);
        assertThat(row.get("completed_at")).isNull();

        Party found = parties.findById(id).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(found.getMembers()).hasSize(2);
        assertThat(found.getDepartureLocation()).isEqualTo(DEPARTURE);
        assertThat(found.getDestinationLocation()).isEqualTo(DESTINATION);
        assertThat(found.getEstimatedFare()).isEqualTo(9000);
    }

    @DisplayName("save - 새 예약 방도 반경 500, completed_at NULL 로 저장되고 다시 읽힌다")
    @Test
    void save_reservationWithoutUnmappedColumns() {

        /*
         * settings
         */
        Instant now = Instant.now();
        Party reservation = Party.reserve(방장, DEPARTURE, DESTINATION, "광주송정역", "전남대", new Capacity(3), now, now.plus(Duration.ofDays(1)));

        /*
         * test
         */
        Long id = save(reservation);

        /*
         * verify
         */
        Map<String, Object> row = jdbc.queryForMap(RADIUS_AND_COMPLETED_AT, id);
        assertThat(row).containsEntry("departure_radius", 500).containsEntry("destination_radius", 500);
        assertThat(row.get("completed_at")).isNull();

        Party found = parties.findById(id).orElseThrow();
        assertThat(found.getRoomType()).isEqualTo(RoomType.RESERVED);
        assertThat(found.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(found.hostId()).isEqualTo(방장);
    }

    @DisplayName("findById - 구버전 서버가 반경 100·completed_at 을 채운 행도 읽히고, 고쳐 저장해도 그 값은 건드리지 않는다")
    @Test
    void findById_rowWrittenByOldServer() {

        /*
         * settings
         */
        Long id = jdbc.queryForObject("""
                INSERT INTO match_room (capacity, departure_lat, departure_lng, departure_radius, destination_lat, destination_lng, destination_radius,
                                        created_at, departure_place, destination_place, status, version, room_type, estimated_fare, estimated_time, route,
                                        host_id, completed_at)
                VALUES (2, 35.1595, 126.8526, 100, 35.1768, 126.9055, 100, now(), '광주송정역', '전남대', 'COMPLETED', 1, 'REALTIME', 9000, 20, 'x', ?, now())
                RETURNING id
                """, Long.class, 방장);
        created.add(id);
        jdbc.update("INSERT INTO user_match_room (match_id, user_id, created_at) VALUES (?, ?, now() - interval '1 minute'), (?, ?, now())",
                id, 방장, id, 동승자);
        Timestamp completedAt = jdbc.queryForObject("SELECT completed_at FROM match_room WHERE id = ?", Timestamp.class, id);

        /*
         * test
         */
        Party loaded = parties.findById(id).orElseThrow();
        tx.executeWithoutResult(status -> {
            Party locked = parties.findByIdForUpdate(id).orElseThrow();
            locked.leave(동승자);   // COMPLETED → ACTIVE
            parties.save(locked);
        });

        /*
         * verify
         */
        assertThat(loaded.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(loaded.getMembers()).hasSize(2);
        assertThat(loaded.isHost(방장)).isTrue();

        // 새 코드는 세 컬럼을 쓰지도 지우지도 않는다 - 읽는 곳이 없어 남은 값은 다음 릴리스의 DROP 까지 그대로 둔다
        Map<String, Object> row = jdbc.queryForMap(RADIUS_AND_COMPLETED_AT, id);
        assertThat(row).containsEntry("departure_radius", 100).containsEntry("destination_radius", 100);
        assertThat(row.get("completed_at")).isEqualTo(completedAt);
        assertThat(parties.findById(id).orElseThrow().getStatus()).isEqualTo(PartyStatus.ACTIVE);
    }

    private Long save(Party party) {
        Long id = parties.save(party).getId();
        created.add(id);
        return id;
    }
}
