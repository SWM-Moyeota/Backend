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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  V28(약속 시각 알림·출발 재촉 컬럼, 부분 인덱스)과 스케줄러의 대상 조회, 처리 결과 저장이 실제 Postgres 에서 맞게 도는지 본다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class MeetingNotificationPostgresTest {
    // 다른 테스트가 남긴 방과 섞이지 않게 제주 좌표를 쓴다
    private static final Location DEPARTURE = new Location(33.4996, 126.5312);
    private static final Location DESTINATION = new Location(33.2541, 126.5600);

    @Autowired Parties parties;
    @Autowired MeetingNotificationService notificationService;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    private final Long 방장 = ThreadLocalRandom.current().nextLong(6_000_000_000L, 7_000_000_000L);
    private final Long 동승자 = 방장 + 1;
    private final List<Long> created = new ArrayList<>();

    @AfterEach
    void tearDown() {
        created.forEach(id -> {
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match_room WHERE id = ?", id);
        });
    }

    // ───────────────────────── V28 ─────────────────────────

    @Test
    void V28_은_알림_컬럼과_열린_확정_방_부분_인덱스를_만든다() {
        Map<String, Object> notifiedAt = jdbc.queryForMap(
                "SELECT data_type, is_nullable FROM information_schema.columns WHERE table_name = 'match_room' AND column_name = 'meeting_notified_at'");
        Map<String, Object> count = jdbc.queryForMap(
                "SELECT data_type, is_nullable, column_default FROM information_schema.columns WHERE table_name = 'match_room' AND column_name = 'departure_reminder_count'");
        String index = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'match_room' AND indexname = 'idx_match_room_open_confirmed_departure'", String.class);

        assertThat(notifiedAt).containsEntry("data_type", "timestamp with time zone").containsEntry("is_nullable", "YES");
        assertThat(count).containsEntry("data_type", "smallint").containsEntry("is_nullable", "NO").containsEntry("column_default", "0");
        assertThat(index).contains("(departure_at)").contains("WHERE").contains("'ACTIVE'").contains("'COMPLETED'").contains("departure_at IS NOT NULL");
    }

    // ───────────────────────── 대상 조회 ─────────────────────────
    // 1분마다 도는 실제 스케줄러가 먼저 처리하지 않게 방을 만들고 조회하는 데까지 한 트랜잭션 안에서 하고 되돌린다

    @Test
    void 약속_시각_알림은_약속_시각이_지났고_처리하지_않은_열린_확정_방만_고른다() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        List<List<Long>> result = inRolledBackTransaction(() -> {
            Long 지난방 = realtimeRoom(PartyStatus.COMPLETED, now.minus(Duration.ofMinutes(1)));
            Long 정각방 = realtimeRoom(PartyStatus.ACTIVE, now);
            Long 지난예약 = reservedRoom(PartyStatus.ACTIVE, now.minusSeconds(10));
            Long 늦은방 = realtimeRoom(PartyStatus.COMPLETED, now.minus(Duration.ofHours(2)));   // 고르기는 하고 30분 지연은 도메인이 가른다
            Long 아직인방 = realtimeRoom(PartyStatus.COMPLETED, now.plusSeconds(1));
            Long 처리한방 = realtimeRoom(PartyStatus.COMPLETED, now.minus(Duration.ofMinutes(1)));
            setColumn(처리한방, "meeting_notified_at", at(now.minusSeconds(30)));
            Long 확정전방 = realtimeRoom(PartyStatus.COMPLETED, null);
            Long 끝난방 = realtimeRoom(PartyStatus.FINISHED, now.minus(Duration.ofMinutes(1)));
            Long 취소된방 = reservedRoom(PartyStatus.CANCELED, now.minus(Duration.ofMinutes(1)));

            return List.of(parties.findMeetingNoticeTargets(now),
                    List.of(지난방, 정각방, 지난예약, 늦은방), List.of(아직인방, 처리한방, 확정전방, 끝난방, 취소된방));
        });

        assertThat(result.get(0)).containsAll(result.get(1)).doesNotContainAnyElementsOf(result.get(2));
    }

    @Test
    void 출발_재촉은_처리한_차례_수마다_약속_시각_뒤_30분_1시간_2시간_4시간에_고른다() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        List<Duration> offsets = List.of(Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(4));

        List<List<Long>> result = inRolledBackTransaction(() -> {
            List<Long> due = new ArrayList<>();
            List<Long> notDue = new ArrayList<>();

            for(int count = 0; count < offsets.size(); count++) {
                Long 정각방 = realtimeRoom(PartyStatus.COMPLETED, now.minus(offsets.get(count)));
                setColumn(정각방, "departure_reminder_count", count);
                due.add(정각방);

                Long 일초전방 = realtimeRoom(PartyStatus.COMPLETED, now.minus(offsets.get(count)).plusSeconds(1));
                setColumn(일초전방, "departure_reminder_count", count);
                notDue.add(일초전방);
            }

            Long 밀린방 = reservedRoom(PartyStatus.ACTIVE, now.minus(Duration.ofHours(3)));   // 차례 0 인데 3시간이 지났다
            due.add(밀린방);

            Long 다쓴방 = realtimeRoom(PartyStatus.COMPLETED, now.minus(Duration.ofHours(5)));
            setColumn(다쓴방, "departure_reminder_count", 4);
            notDue.add(다쓴방);
            notDue.add(realtimeRoom(PartyStatus.FINISHED, now.minus(Duration.ofHours(1))));
            notDue.add(reservedRoom(PartyStatus.CANCELED, now.minus(Duration.ofHours(1))));
            notDue.add(realtimeRoom(PartyStatus.COMPLETED, null));

            return List.of(parties.findDepartureReminderTargets(now), due, notDue);
        });

        assertThat(result.get(0)).containsAll(result.get(1)).doesNotContainAnyElementsOf(result.get(2));
    }

    // ───────────────────────── 처리 저장 ─────────────────────────

    @Test
    void 약속_시각_알림과_출발_재촉_처리가_저장되고_같은_차례는_한_번만_처리된다() {
        // 실제 스케줄러가 먼저 처리하지 않게 약속 시각을 3시간 뒤로 두고, 서비스에 그 뒤의 시각을 넘긴다
        Instant meetingAt = Instant.now().plus(Duration.ofHours(3)).truncatedTo(ChronoUnit.MICROS);
        Party party = Party.reserve(방장, DEPARTURE, DESTINATION, "제주공항", "서귀포", new Capacity(3), Instant.now(), meetingAt);
        party.join(동승자);
        Long id = parties.save(party).getId();
        created.add(id);
        Instant notifiedAt = meetingAt.plusSeconds(10);

        notificationService.notifyMeetingTime(id, notifiedAt);
        notificationService.notifyMeetingTime(id, notifiedAt.plusSeconds(60));

        assertThat(timestamp(id, "meeting_notified_at")).isEqualTo(notifiedAt);

        notificationService.remindDeparture(id, meetingAt.plus(Duration.ofMinutes(30)));
        notificationService.remindDeparture(id, meetingAt.plus(Duration.ofMinutes(31)));

        assertThat(reminderCount(id)).isEqualTo(1);

        notificationService.remindDeparture(id, meetingAt.plus(Duration.ofHours(2)));   // 1시간·2시간 차례가 밀려 한 번에 넘어간다

        assertThat(reminderCount(id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT version FROM match_room WHERE id = ?", Long.class, id))
                .as("알림 기록은 방 상세·SSE 에 보이지 않아 version 을 올리지 않는다(참여로 1)").isEqualTo(1L);
    }

    // ───────────────────────── 도우미 ─────────────────────────

    private <T> T inRolledBackTransaction(Supplier<T> work) {
        return tx.execute(status -> {
            status.setRollbackOnly();
            return work.get();
        });
    }

    /** 방장·동승자 둘이 있는 실시간 방. departureAt 이 있으면 그 시각으로 약속을 확정한 방이다 */
    private Long realtimeRoom(PartyStatus status, Instant departureAt) {
        Party party = Party.open(방장, DEPARTURE, DESTINATION, "제주공항", "서귀포", new Capacity(3), Instant.now(),
                Radius.DEFAULT, Radius.DEFAULT, 30000, 60, "_p~iF~ps|U_ulLnnqC");
        party.join(동승자);
        Long id = parties.save(party).getId();
        jdbc.update("UPDATE match_room SET status = ?, departure_at = ? WHERE id = ?", status.name(), departureAt == null ? null : at(departureAt), id);
        return id;
    }

    /** 방장·동승자 둘이 있는 예약 방 - 출발 시각만 departureAt 으로 옮긴다 */
    private Long reservedRoom(PartyStatus status, Instant departureAt) {
        Party party = Party.reserve(방장, DEPARTURE, DESTINATION, "제주공항", "서귀포", new Capacity(3), Instant.now(), Instant.now().plus(Duration.ofHours(1)));
        party.join(동승자);
        Long id = parties.save(party).getId();
        jdbc.update("UPDATE match_room SET status = ?, departure_at = ? WHERE id = ?", status.name(), at(departureAt), id);
        return id;
    }

    private void setColumn(Long id, String column, Object value) {
        jdbc.update("UPDATE match_room SET " + column + " = ? WHERE id = ?", value, id);
    }

    private Instant timestamp(Long id, String column) {
        Timestamp value = jdbc.queryForObject("SELECT " + column + " FROM match_room WHERE id = ?", Timestamp.class, id);
        return value == null ? null : value.toInstant();
    }

    private int reminderCount(Long id) {
        return jdbc.queryForObject("SELECT departure_reminder_count FROM match_room WHERE id = ?", Integer.class, id);
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
