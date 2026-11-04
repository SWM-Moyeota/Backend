package team.codingforest.moyeota.matching.party.infrastructure;

import org.junit.jupiter.api.AfterEach;
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
import team.codingforest.moyeota.matching.party.domain.Radius;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  방장이 확정한 약속 시간이 실제 Postgres 의 match_room.departure_at 에 남는지 본다.
 *  서비스와 같은 순서(행 잠금 조회 → 확정 → 저장)로 한 트랜잭션 안에서 고친다 - V24 CHECK 가 실시간 방의 departure_at 을 막지 않는지도 여기서 걸린다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class PartyConfirmPostgresTest {
    // 다른 테스트가 남긴 방과 섞이지 않게 대전 좌표를 쓴다
    private static final Location DEPARTURE = new Location(36.3504, 127.3845);
    private static final Location DESTINATION = new Location(36.3320, 127.4342);
    private static final long HOST_ID = 987_654_331L;
    private static final long MEMBER_ID = 987_654_332L;

    @Autowired Parties parties;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        created.forEach(id -> {
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match_room WHERE id = ?", id);
        });
    }

    @Test
    void 확정한_약속_시간이_departure_at_에_저장되고_다시_읽힌다() {
        Party party = Party.open(HOST_ID, DEPARTURE, DESTINATION, "대전역", "한밭대", new Capacity(3), Instant.now(),
                Radius.DEFAULT, Radius.DEFAULT, 12000, 25, "_p~iF~ps|U_ulLnnqC");
        party.join(MEMBER_ID);
        Long id = parties.save(party).getId();
        created.add(id);
        Instant meetingAt = Instant.now().plus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.SECONDS);

        tx.executeWithoutResult(status -> {
            Party locked = parties.findByIdForUpdate(id).orElseThrow();
            locked.confirm(HOST_ID, meetingAt, Instant.now());
            parties.save(locked);
        });

        Timestamp stored = jdbc.queryForObject("SELECT departure_at FROM match_room WHERE id = ?", Timestamp.class, id);
        Party found = parties.findById(id).orElseThrow();

        assertThat(stored.toInstant()).isEqualTo(meetingAt);
        assertThat(found.getDepartureAt()).isEqualTo(meetingAt);
        assertThat(found.isConfirmed()).isTrue();
        assertThat(found.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(found.getVersion()).isEqualTo(2);
    }
}
