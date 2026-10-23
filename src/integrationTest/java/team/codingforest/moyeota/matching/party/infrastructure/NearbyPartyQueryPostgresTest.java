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
import team.codingforest.moyeota.matching.party.domain.Radius;
import team.codingforest.moyeota.matching.party.dto.NearbyPartySummary;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
        Party party = open(3);
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
        Party party = open(2);
        party.join(2L);
        Long id = save(party);

        List<NearbyPartySummary> result = parties.findMatchesWithinRadius(DEPARTURE.latitude(), DEPARTURE.longitude(),
                DESTINATION.latitude(), DESTINATION.longitude(), RADIUS);

        assertThat(result).extracting(NearbyPartySummary::id).doesNotContain(id);
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

    private static Party open(int capacity) {
        return Party.open(1L, DEPARTURE, DESTINATION, "대전역", "충남대", new Capacity(capacity), Instant.now(),
                new Radius(100), new Radius(100), 12000, 25, "_p~iF~ps|U_ulLnnqC");
    }
}
