package team.codingforest.moyeota.matching.party;

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
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  모집 만료 스케줄러의 대상 조회(실시간 방은 만든 지 15분, 예약 방은 출발 시각)와 만료 결과가 실제 Postgres 에 맞게 도는지 본다.
 *  15분은 혼자면 접는 기준이지 모집 마감이 아니다 - 2명 이상인 방은 고르지도 닫지도 않는다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class PartyRecruitingExpirePostgresTest {
    // 다른 테스트가 남긴 방과 섞이지 않게 포항 좌표를 쓴다
    private static final Location DEPARTURE = new Location(36.0190, 129.3435);
    private static final Location DESTINATION = new Location(36.0830, 129.4020);
    private static final Duration RECRUITING_TTL = Duration.ofMinutes(15);

    @Autowired Parties parties;
    @Autowired PartyService partyService;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    // 다른 테스트·이전 실행의 방과 섞이지 않게 매번 새 번호를 쓴다
    private final Long 방장 = ThreadLocalRandom.current().nextLong(5_000_000_000L, 6_000_000_000L);
    private final Long 동승자 = 방장 + 1;
    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        created.forEach(id -> {
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match_room WHERE id = ?", id);
        });
    }

    // ───────────────────────── 만료 대상 조회 ─────────────────────────
    // 실제 스케줄러(1분마다)가 먼저 닫지 못하게 방을 만들고 조회하는 데까지 한 트랜잭션 안에서 하고 되돌린다

    @Test
    void 만든_지_15분이_지난_혼자인_실시간_방만_대상이고_2명_이상이면_15분이_지나도_대상이_아니다() {
        Instant now = Instant.now();

        List<List<Long>> result = inRolledBackTransaction(() -> {
            Long 혼자_오래된방 = realtimeRoom(now.minus(Duration.ofMinutes(16)), false);
            Long 혼자_최근방 = realtimeRoom(now.minus(Duration.ofMinutes(14)), false);
            Long 둘_오래된방 = realtimeRoom(now.minus(Duration.ofMinutes(16)), true);
            Long 혼자_취소된방 = realtimeRoom(now.minus(Duration.ofMinutes(16)), false);
            jdbc.update("UPDATE match_room SET status = 'CANCELED' WHERE id = ?", 혼자_취소된방);

            return List.of(parties.findRecruitingExpired(now.minus(RECRUITING_TTL), now),
                    List.of(혼자_오래된방), List.of(혼자_최근방, 둘_오래된방, 혼자_취소된방));
        });

        assertThat(result.get(0)).containsAll(result.get(1)).doesNotContainAnyElementsOf(result.get(2));
    }

    @Test
    void 출발_시각이_된_혼자인_예약_방만_대상이고_2명_이상이거나_출발_전이면_대상이_아니다() {
        Instant now = Instant.now();

        List<List<Long>> result = inRolledBackTransaction(() -> {
            Long 혼자_출발시각지남 = reservedRoom(now.minusSeconds(10), false);
            Long 혼자_출발전 = reservedRoom(now.plus(Duration.ofMinutes(10)), false);
            Long 둘_출발시각지남 = reservedRoom(now.minusSeconds(10), true);

            return List.of(parties.findRecruitingExpired(now.minus(RECRUITING_TTL), now),
                    List.of(혼자_출발시각지남), List.of(혼자_출발전, 둘_출발시각지남));
        });

        assertThat(result.get(0)).containsAll(result.get(1)).doesNotContainAnyElementsOf(result.get(2));
    }

    // ───────────────────────── 만료 처리 ─────────────────────────

    @Test
    void 혼자인_방을_만료하면_CANCELED_로_닫힌다() {
        Long id = track(realtimeRoom(Instant.now().minus(Duration.ofMinutes(16)), false));

        partyService.expireRecruiting(id);

        assertThat(status(id)).isEqualTo("CANCELED");
        assertThat(parties.findById(id).orElseThrow().getStatus()).isEqualTo(PartyStatus.CANCELED);
    }

    @Test
    void 두_명_이상인_방은_15분이_지나_만료를_불러도_ACTIVE_그대로다() {
        Long id = track(realtimeRoom(Instant.now().minus(Duration.ofMinutes(16)), true));
        long versionBefore = parties.findById(id).orElseThrow().getVersion();

        partyService.expireRecruiting(id);

        Party party = parties.findById(id).orElseThrow();
        assertThat(status(id)).isEqualTo("ACTIVE");
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(party.getVersion()).as("아무것도 바꾸지 않는다").isEqualTo(versionBefore);
        assertThat(party.memberIds()).containsExactlyInAnyOrder(방장, 동승자);
    }

    // ───────────────────────── 도우미 ─────────────────────────

    private <T> T inRolledBackTransaction(Supplier<T> work) {
        return tx.execute(status -> {
            status.setRollbackOnly();
            return work.get();
        });
    }

    /** 모집 중(ACTIVE)인 실시간 방. withCompanion 이면 동승자가 한 명 더 있다 */
    private Long realtimeRoom(Instant createdAt, boolean withCompanion) {
        Party party = Party.create(방장, DEPARTURE, DESTINATION, "포항역", "포스텍", new Capacity(3), Instant.now(),
                9000, 20, "_p~iF~ps|U_ulLnnqC");
        if(withCompanion) party.join(동승자, Instant.now());
        Long id = parties.save(party).getId();
        jdbc.update("UPDATE match_room SET created_at = ? WHERE id = ?", at(createdAt), id);
        return id;
    }

    /** 모집 중(ACTIVE)인 예약 방 - 출발 시각만 departureAt 으로 옮긴다 */
    private Long reservedRoom(Instant departureAt, boolean withCompanion) {
        Party party = Party.reserve(방장, DEPARTURE, DESTINATION, "포항역", "포스텍", new Capacity(3), Instant.now(), Instant.now().plus(Duration.ofHours(1)));
        if(withCompanion) party.join(동승자, Instant.now());
        Long id = parties.save(party).getId();
        jdbc.update("UPDATE match_room SET departure_at = ? WHERE id = ?", at(departureAt), id);
        return id;
    }

    private Long track(Long id) {
        created.add(id);
        return id;
    }

    private String status(Long partyId) {
        return jdbc.queryForObject("SELECT status FROM match_room WHERE id = ?", String.class, partyId);
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
