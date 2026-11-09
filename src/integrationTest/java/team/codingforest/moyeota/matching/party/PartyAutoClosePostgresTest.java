package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.MeetingPlan;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
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
 *  정리 스케줄러의 대상 조회(약속이 있는 방은 departure_at, 약속이 없는 실시간 방은 created_at 에서 1일)와
 *  자동 정리 결과(상태·탑승 기록)가 실제 Postgres 에 맞게 도는지 본다
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
    void 미확정_실시간_방은_만든_지_1일이_지나야_정리_대상이고_닫힌_방은_고르지_않는다() {
        Instant now = Instant.now();

        List<List<Long>> result = inRolledBackTransaction(() -> {
            Long 오래된모집방 = realtimeRoom(PartyStatus.ACTIVE, now.minus(Duration.ofHours(25)), null);
            Long 최근모집방 = realtimeRoom(PartyStatus.ACTIVE, now.minus(Duration.ofHours(23)), null);
            Long 끝난방 = realtimeRoom(PartyStatus.FINISHED, now.minus(Duration.ofDays(2)), null);
            Long 취소된방 = realtimeRoom(PartyStatus.CANCELED, now.minus(Duration.ofDays(2)), null);

            return List.of(parties.findAutoCloseTargets(now.minus(AUTO_CLOSE_AFTER)),
                    List.of(오래된모집방), List.of(최근모집방, 끝난방, 취소된방));
        });

        assertThat(result.get(0)).containsAll(result.get(1)).doesNotContainAnyElementsOf(result.get(2));
    }

    @Test
    void 확정한_실시간_방은_만든_시각이_아니라_약속_시각에서_1일이_지나야_정리_대상이다() {
        Instant now = Instant.now();

        List<List<Long>> result = inRolledBackTransaction(() -> {
            Long 오래된방_약속은_아직 = realtimeRoom(PartyStatus.COMPLETED, now.minus(Duration.ofDays(2)), now.plus(Duration.ofMinutes(10)));
            Long 오래된방_약속은_최근 = realtimeRoom(PartyStatus.COMPLETED, now.minus(Duration.ofDays(2)), now.minus(Duration.ofHours(23)));
            Long 최근방_약속은_오래전 = realtimeRoom(PartyStatus.COMPLETED, now.minus(Duration.ofHours(23)), now.minus(Duration.ofHours(25)));
            Long 끝난방_약속은_오래전 = realtimeRoom(PartyStatus.FINISHED, now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(2)));

            return List.of(parties.findAutoCloseTargets(now.minus(AUTO_CLOSE_AFTER)),
                    List.of(최근방_약속은_오래전), List.of(오래된방_약속은_아직, 오래된방_약속은_최근, 끝난방_약속은_오래전));
        });

        assertThat(result.get(0)).containsAll(result.get(1)).doesNotContainAnyElementsOf(result.get(2));
    }

    @Test
    void 예약_방은_만든_시각이_아니라_출발_시각에서_1일이_지나야_정리_대상이다() {
        Instant now = Instant.now();

        List<List<Long>> result = inRolledBackTransaction(() -> {
            Long 지난예약 = reservedRoom(PartyStatus.ACTIVE, now.minus(Duration.ofHours(25)));
            Long 최근예약 = reservedRoom(PartyStatus.ACTIVE, now.minus(Duration.ofHours(23)));   // 만든 지는 3일이 지났다
            Long 끝난예약 = reservedRoom(PartyStatus.FINISHED, now.minus(Duration.ofDays(2)));
            Long 취소된예약 = reservedRoom(PartyStatus.CANCELED, now.minus(Duration.ofDays(2)));

            return List.of(parties.findAutoCloseTargets(now.minus(AUTO_CLOSE_AFTER)),
                    List.of(지난예약), List.of(최근예약, 끝난예약, 취소된예약));
        });

        assertThat(result.get(0)).containsAll(result.get(1)).doesNotContainAnyElementsOf(result.get(2));
    }

    // ───────────────────────── 자동 정리 ─────────────────────────

    @Test
    void 도착한_멤버가_있으면_FINISHED_로_닫히고_탑승_기록이_남는다() {
        Long id = confirmedRoomOfThree();
        partyService.arrive(id, 방장, DEPARTURE_LAT, DEPARTURE_LNG);

        partyService.autoClose(id);

        assertThat(status(id)).isEqualTo("FINISHED");
        assertThat(parties.findById(id).orElseThrow().riderIds()).as("도착한 멤버가 탄 사람이다").containsExactly(방장);
        assertThat(column("arrived_at", id, 방장)).isNotNull();
        assertThat(column("arrived_at", id, 동승자)).isNull();
        assertThat(column("arrived_at", id, 늦은사람)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ride_record WHERE match_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(rideRecordService.findMyRides(방장, null, 20).rides()).extracting(r -> r.partyId()).containsExactly(id);
    }

    @Test
    void 아무도_도착하지_않았으면_CANCELED_로_닫히고_탑승_기록이_없다() {
        Long id = confirmedRoomOfThree();

        partyService.autoClose(id);

        assertThat(status(id)).isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ride_record WHERE match_id = ?", Integer.class, id)).isZero();
        assertThat(parties.countFinishedRides(List.of(방장, 동승자, 늦은사람))).isEmpty();
    }

    @Test
    void 약속을_확정하지_않은_방은_CANCELED_로_닫히고_탑승_기록이_없다() {
        Party party = Party.create(방장, DEPARTURE, DESTINATION, "울산역", "울산대", new Capacity(3), Instant.now(),
                9000, 20, "_p~iF~ps|U_ulLnnqC");
        party.join(동승자, Instant.now());
        Long id = parties.save(party).getId();
        created.add(id);

        partyService.autoClose(id);

        assertThat(status(id)).isEqualTo("CANCELED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ride_record WHERE match_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void 이미_닫힌_방은_다시_정리해도_상태와_탑승_기록이_그대로다() {
        Long id = confirmedRoomOfThree();
        partyService.arrive(id, 방장, DEPARTURE_LAT, DEPARTURE_LNG);
        partyService.autoClose(id);

        partyService.autoClose(id);

        assertThat(status(id)).isEqualTo("FINISHED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ride_record WHERE match_id = ?", Integer.class, id)).isEqualTo(1);
    }

    // ───────────────────────── 도우미 ─────────────────────────

    private <T> T inRolledBackTransaction(java.util.function.Supplier<T> work) {
        return tx.execute(status -> {
            status.setRollbackOnly();
            return work.get();
        });
    }

    /** 방장 혼자인 실시간 방. departureAt 이 있으면 그 시각으로 약속을 확정한 방이다 */
    private Long realtimeRoom(PartyStatus status, Instant createdAt, Instant departureAt) {
        Party party = Party.create(방장, DEPARTURE, DESTINATION, "울산역", "울산대", new Capacity(3), Instant.now(),
                9000, 20, "_p~iF~ps|U_ulLnnqC");
        Long id = parties.save(party).getId();
        jdbc.update("UPDATE match_room SET status = ?, created_at = ?, departure_at = ? WHERE id = ?",
                status.name(), at(createdAt), departureAt == null ? null : at(departureAt), id);
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

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     *  방장·동승자·늦은사람 셋이 찬 실시간 방을 확정한 뒤 약속 시각을 방금 지난 시각(30초 전)으로 돌린다 - 바로 도착을 누를 수 있다.
     *  확정은 지금부터 5분 뒤부터만 받으므로 10분 뒤로 확정하고 행을 직접 고친다
     */
    private Long confirmedRoomOfThree() {
        Party party = Party.create(방장, DEPARTURE, DESTINATION, "울산역", "울산대", new Capacity(3), Instant.now(),
                9000, 20, "_p~iF~ps|U_ulLnnqC");
        party.join(동승자, Instant.now());
        party.join(늦은사람, Instant.now());
        Long id = parties.save(party).getId();
        created.add(id);

        partyService.confirm(id, 방장, MeetingPlan.at(Instant.now().plus(Duration.ofMinutes(10))));
        jdbc.update("UPDATE match_room SET departure_at = now() - interval '30 seconds' WHERE id = ?", id);

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
