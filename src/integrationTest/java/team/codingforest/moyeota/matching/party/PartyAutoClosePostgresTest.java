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
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.Radius;
import team.codingforest.moyeota.matching.record.RideRecordService;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  정리 스케줄러의 대상 조회(실시간 방은 created_at, 예약 방은 departure_at 에서 1일)와 자동 정리 결과(노쇼·상태·탑승 기록)가
 *  실제 Postgres 에 맞게 도는지 본다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class PartyAutoClosePostgresTest {
    // 다른 테스트가 남긴 방과 섞이지 않게 울산 좌표를 쓴다
    private static final double DEPARTURE_LAT = 35.5384;
    private static final double DEPARTURE_LNG = 129.3114;
    private static final Location DEPARTURE = new Location(DEPARTURE_LAT, DEPARTURE_LNG);
    private static final Location DESTINATION = new Location(35.5714, 129.1897);
    private static final Duration AUTO_CLOSE_AFTER = Duration.ofDays(1);

    @Autowired Parties parties;
    @Autowired PartyService partyService;
    @Autowired RideRecordService rideRecordService;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    // 다른 테스트·이전 실행의 방과 섞이지 않게 매번 새 번호를 쓴다
    private final Long 방장 = ThreadLocalRandom.current().nextLong(4_000_000_000L, 5_000_000_000L);
    private final Long 동승자 = 방장 + 1;
    private final Long 늦은사람 = 방장 + 2;
    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        created.forEach(id -> {
            jdbc.update("DELETE FROM ride_record WHERE match_id = ?", id);
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match_room WHERE id = ?", id);
        });
    }

    // ───────────────────────── 정리 대상 조회 ─────────────────────────
    // 모집 정리 스케줄러(1분마다)가 오래된 ACTIVE 방을 먼저 닫지 못하게, 방을 만들고 조회하는 데까지 한 트랜잭션 안에서 하고 되돌린다

    @Test
    void 실시간_방은_만든_지_1일이_지나야_정리_대상이고_ACTIVE_COMPLETED_만_고른다() {
        Instant now = Instant.now();

        List<List<Long>> result = inRolledBackTransaction(() -> {
            Long 오래된방 = realtimeRoom(PartyStatus.COMPLETED, now.minus(Duration.ofHours(25)));
            Long 오래된모집방 = realtimeRoom(PartyStatus.ACTIVE, now.minus(Duration.ofHours(25)));
            Long 최근방 = realtimeRoom(PartyStatus.COMPLETED, now.minus(Duration.ofHours(23)));
            Long 끝난방 = realtimeRoom(PartyStatus.FINISHED, now.minus(Duration.ofDays(2)));
            Long 취소된방 = realtimeRoom(PartyStatus.CANCELED, now.minus(Duration.ofDays(2)));

            return List.of(parties.findAutoCloseTargets(now.minus(AUTO_CLOSE_AFTER)),
                    List.of(오래된방, 오래된모집방), List.of(최근방, 끝난방, 취소된방));
        });

        assertThat(result.get(0)).containsAll(result.get(1)).doesNotContainAnyElementsOf(result.get(2));
    }

    @Test
    void 약속을_확정한_실시간_방도_약속_시간이_아니라_만든_시각으로_가른다() {
        Instant now = Instant.now();

        List<List<Long>> result = inRolledBackTransaction(() -> {
            Long 최근방_약속은_오래전 = realtimeRoom(PartyStatus.COMPLETED, now.minus(Duration.ofHours(23)));
            setDepartureAt(최근방_약속은_오래전, now.minus(Duration.ofDays(2)));
            Long 오래된방_약속은_최근 = realtimeRoom(PartyStatus.COMPLETED, now.minus(Duration.ofHours(25)));
            setDepartureAt(오래된방_약속은_최근, now.minus(Duration.ofMinutes(10)));

            return List.of(parties.findAutoCloseTargets(now.minus(AUTO_CLOSE_AFTER)),
                    List.of(오래된방_약속은_최근), List.of(최근방_약속은_오래전));
        });

        assertThat(result.get(0)).containsAll(result.get(1)).doesNotContainAnyElementsOf(result.get(2));
    }

    @Test
    void 예약_방은_만든_시각이_아니라_출발_시각에서_1일이_지나야_정리_대상이다() {
        Instant now = Instant.now();

        List<List<Long>> result = inRolledBackTransaction(() -> {
            Long 지난예약 = reservedRoom(PartyStatus.COMPLETED, now.minus(Duration.ofHours(25)));
            Long 지난모집예약 = reservedRoom(PartyStatus.ACTIVE, now.minus(Duration.ofHours(25)));
            Long 최근예약 = reservedRoom(PartyStatus.COMPLETED, now.minus(Duration.ofHours(23)));   // 만든 지는 3일이 지났다
            Long 끝난예약 = reservedRoom(PartyStatus.FINISHED, now.minus(Duration.ofDays(2)));
            Long 취소된예약 = reservedRoom(PartyStatus.CANCELED, now.minus(Duration.ofDays(2)));

            return List.of(parties.findAutoCloseTargets(now.minus(AUTO_CLOSE_AFTER)),
                    List.of(지난예약, 지난모집예약), List.of(최근예약, 끝난예약, 취소된예약));
        });

        assertThat(result.get(0)).containsAll(result.get(1)).doesNotContainAnyElementsOf(result.get(2));
    }

    // ───────────────────────── 자동 정리 ─────────────────────────

    @Test
    void 도착한_멤버가_있으면_FINISHED_로_닫히고_도착하지_않은_멤버는_노쇼로_저장되며_도착한_멤버에게만_탑승_기록이_남는다() {
        Long id = confirmedRoomOfThree();
        partyService.arrive(id, 방장, DEPARTURE_LAT, DEPARTURE_LNG);

        List<Long> noShowMemberIds = partyService.autoClose(id);

        assertThat(noShowMemberIds).containsExactly(동승자, 늦은사람);
        assertThat(status(id)).isEqualTo("FINISHED");
        assertThat(column("no_show_at", id, 방장)).isNull();
        assertThat(column("no_show_at", id, 동승자)).isNotNull();
        assertThat(column("no_show_at", id, 늦은사람)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ride_record WHERE match_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(rideRecordService.findMyRides(방장, null, 20).rides()).extracting(r -> r.partyId()).containsExactly(id);
        assertThat(rideRecordService.findMyRides(동승자, null, 20).rides()).as("노쇼에게는 탑승 기록이 없다").isEmpty();
    }

    @Test
    void 아무도_도착하지_않았으면_CANCELED_로_닫히고_전원이_노쇼로_저장되며_탑승_기록이_없다() {
        Long id = confirmedRoomOfThree();

        List<Long> noShowMemberIds = partyService.autoClose(id);

        assertThat(noShowMemberIds).containsExactly(방장, 동승자, 늦은사람);
        assertThat(status(id)).isEqualTo("CANCELED");
        assertThat(column("no_show_at", id, 방장)).isNotNull();
        assertThat(column("no_show_at", id, 동승자)).isNotNull();
        assertThat(column("no_show_at", id, 늦은사람)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ride_record WHERE match_id = ?", Integer.class, id)).isZero();
        assertThat(parties.countFinishedRides(List.of(방장, 동승자, 늦은사람))).isEmpty();
    }

    // ───────────────────────── 도우미 ─────────────────────────

    private <T> T inRolledBackTransaction(java.util.function.Supplier<T> work) {
        return tx.execute(status -> {
            status.setRollbackOnly();
            return work.get();
        });
    }

    private Long realtimeRoom(PartyStatus status, Instant createdAt) {
        Party party = Party.open(방장, DEPARTURE, DESTINATION, "울산역", "울산대", new Capacity(3), Instant.now(),
                Radius.DEFAULT, Radius.DEFAULT, 9000, 20, "_p~iF~ps|U_ulLnnqC");
        Long id = parties.save(party).getId();
        jdbc.update("UPDATE match_room SET status = ?, created_at = ? WHERE id = ?", status.name(), at(createdAt), id);
        return id;
    }

    /** 만든 지 3일이 지난 예약 방 - 출발 시각만 departureAt 으로 옮긴다 */
    private Long reservedRoom(PartyStatus status, Instant departureAt) {
        Party party = Party.reserve(방장, DEPARTURE, DESTINATION, "울산역", "울산대", new Capacity(3), Instant.now(), Instant.now().plus(Duration.ofHours(1)));
        Long id = parties.save(party).getId();
        jdbc.update("UPDATE match_room SET status = ?, created_at = ?, departure_at = ? WHERE id = ?",
                status.name(), at(Instant.now().minus(Duration.ofDays(3))), at(departureAt), id);
        return id;
    }

    private void setDepartureAt(Long id, Instant departureAt) {
        jdbc.update("UPDATE match_room SET departure_at = ? WHERE id = ?", at(departureAt), id);
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** 방장·동승자·늦은사람 셋이 찬 실시간 방을 방금 지난 시각(30초 전)으로 확정한다 - 바로 도착을 누를 수 있다 */
    private Long confirmedRoomOfThree() {
        Party party = Party.open(방장, DEPARTURE, DESTINATION, "울산역", "울산대", new Capacity(3), Instant.now(),
                Radius.DEFAULT, Radius.DEFAULT, 9000, 20, "_p~iF~ps|U_ulLnnqC");
        party.join(동승자);
        party.join(늦은사람);
        Long id = parties.save(party).getId();
        created.add(id);

        partyService.confirm(id, 방장, Instant.now().minusSeconds(30));

        return id;
    }

    private String status(Long partyId) {
        return jdbc.queryForObject("SELECT status FROM match_room WHERE id = ?", String.class, partyId);
    }

    private Instant column(String name, Long partyId, Long memberId) {
        Timestamp value = jdbc.queryForObject("SELECT " + name + " FROM user_match_room WHERE match_id = ? AND user_id = ?",
                Timestamp.class, partyId, memberId);
        return value == null ? null : value.toInstant();
    }
}
