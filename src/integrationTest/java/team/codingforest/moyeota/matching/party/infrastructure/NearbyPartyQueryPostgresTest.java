package team.codingforest.moyeota.matching.party.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.dto.NearbyPartySummary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PostGIS 반경 쿼리(findNearbyParties)는 H2 로 흉내 낼 수 없어 Postgres 에서 확인한다 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class NearbyPartyQueryPostgresTest {
    // 다른 테스트가 남긴 방과 섞이지 않게 강남·판교가 아닌 대전 좌표를 쓴다
    private static final Location DEPARTURE = new Location(36.3504, 127.3845);
    private static final Location DESTINATION = new Location(36.3320, 127.4342);
    private static final int RADIUS = 300;

    @Autowired Parties parties;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        // LIMIT 5 라 남은 방이 쌓이면 다음 실행에서 이 테스트의 방이 밀려날 수 있다
        created.forEach(id -> {
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match_room WHERE id = ?", id);
        });
    }

    @Test
    void 반경_안의_방을_현재_인원과_함께_돌려준다() {
        Party party = create(3);
        party.join(2L);
        Long id = save(party);

        NearbyPartySummary found = findNear(id);

        assertThat(found.capacity()).isEqualTo(3);
        assertThat(found.currentMembers()).isEqualTo(2);
        assertThat(found.departure()).isEqualTo("대전역");
        assertThat(found.departureLat()).isEqualTo(DEPARTURE.latitude());
    }

    @Test
    void 정원이_찬_방은_ACTIVE_가_아니라_후보에서_빠진다() {
        Party party = create(2);
        party.join(2L);
        Long id = save(party);

        List<NearbyPartySummary> result = parties.findMatchesWithinRadius(DEPARTURE.latitude(), DEPARTURE.longitude(),
                DESTINATION.latitude(), DESTINATION.longitude(), RADIUS);

        assertThat(result).extracting(NearbyPartySummary::id).doesNotContain(id);
    }

    @Test
    void 예약_방은_실시간_후보에서_빠진다() {
        Long id = save(Party.reserve(1L, DEPARTURE, DESTINATION, "대전역", "한밭대", new Capacity(3), Instant.now(), Instant.now().plus(java.time.Duration.ofDays(1))));

        List<NearbyPartySummary> result = parties.findMatchesWithinRadius(DEPARTURE.latitude(), DEPARTURE.longitude(),
                DESTINATION.latitude(), DESTINATION.longitude(), RADIUS);

        assertThat(result).extracting(NearbyPartySummary::id).doesNotContain(id);
    }

    @Test
    void 예약_방에는_경로와_요금과_시간이_없어야_한다() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO match_room (capacity, departure_lat, departure_lng, departure_radius, destination_lat, destination_lng, destination_radius,
                                        created_at, departure_place, destination_place, status, version, room_type, departure_at, estimated_fare)
                VALUES (3, 36.35, 127.38, 500, 36.33, 127.43, 500, now(), '대전역', '한밭대', 'ACTIVE', 0, 'RESERVED', now() + interval '1 day', 12000)
                """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void 예약_방에는_출발_시각이_있어야_한다() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO match_room (capacity, departure_lat, departure_lng, departure_radius, destination_lat, destination_lng, destination_radius,
                                        created_at, departure_place, destination_place, status, version, room_type)
                VALUES (3, 36.35, 127.38, 500, 36.33, 127.43, 500, now(), '대전역', '한밭대', 'ACTIVE', 0, 'RESERVED')
                """)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    /** 실시간 방은 방장이 약속 시간을 확정하면 departure_at 이 채워진다 */
    @Test
    void 실시간_방은_확정한_약속_시간을_가질_수_있다() {
        Long id = jdbc.queryForObject("""
                INSERT INTO match_room (capacity, departure_lat, departure_lng, departure_radius, destination_lat, destination_lng, destination_radius,
                                        created_at, departure_place, destination_place, status, version, room_type, departure_at, estimated_fare, estimated_time, route)
                VALUES (3, 36.35, 127.38, 500, 36.33, 127.43, 500, now(), '대전역', '한밭대', 'COMPLETED', 0, 'REALTIME', now() + interval '30 minutes', 12000, 25, 'x')
                RETURNING id
                """, Long.class);
        created.add(id);

        assertThat(id).isNotNull();
    }

    private NearbyPartySummary findNear(Long id) {
        return parties.findMatchesWithinRadius(DEPARTURE.latitude(), DEPARTURE.longitude(),
                        DESTINATION.latitude(), DESTINATION.longitude(), RADIUS).stream()
                .filter(p -> p.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("반경 안의 방이 조회되지 않음 id=" + id));
    }

    private Long save(Party party) {
        Long id = parties.save(party).getId();
        created.add(id);
        return id;
    }

    private static Party create(int capacity) {
        return Party.create(1L, DEPARTURE, DESTINATION, "대전역", "충남대", new Capacity(capacity), Instant.now(),
                12000, 25, "_p~iF~ps|U_ulLnnqC");
    }
}
