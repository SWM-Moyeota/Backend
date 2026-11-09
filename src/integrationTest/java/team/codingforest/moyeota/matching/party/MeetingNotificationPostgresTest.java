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

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 *  알림 대상 조회(departure_at 이 [from, to) 인 열린 확정 방)와 부분 인덱스가 실제 Postgres 에서 맞게 도는지 본다.
 *  알림 처리 표시 컬럼은 없다 - 같은 방을 다시 고르지 않는 것은 스케줄러의 겹치지 않는 1분 구간이 맡는다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class MeetingNotificationPostgresTest {
    // 다른 테스트가 남긴 방과 섞이지 않게 제주 좌표를 쓴다
    private static final Location DEPARTURE = new Location(33.4996, 126.5312);
    private static final Location DESTINATION = new Location(33.2541, 126.5600);

    @Autowired Parties parties;
    @Autowired MeetingNotificationService notificationService;
    @Autowired ReservationReminderService reminderService;
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

    // ───────────────────────── 스키마 ─────────────────────────

    @Test
    void 알림_처리_표시_컬럼은_없고_열린_확정_방_부분_인덱스는_남는다() {
        List<String> columns = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'match_room'", String.class);
        String index = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'match_room' AND indexname = 'idx_match_room_open_confirmed_departure'", String.class);

        assertThat(columns).contains("departure_at")
                .doesNotContain("reminder_sent_at", "meeting_notified_at", "departure_reminder_count");
        assertThat(index).contains("(departure_at)").contains("WHERE").contains("'ACTIVE'").contains("'COMPLETED'").contains("departure_at IS NOT NULL");
    }

    // ───────────────────────── 대상 조회 ─────────────────────────
    // 매분 도는 실제 스케줄러와 섞이지 않게 방을 만들고 조회하는 데까지 한 트랜잭션 안에서 하고 되돌린다

    @Test
    void 열린_확정_방_중_약속_시각이_구간_안인_방만_고르고_구간은_시작을_포함하고_끝을_뺀다() {
        Instant from = Instant.now().plus(Duration.ofHours(3)).truncatedTo(ChronoUnit.MINUTES);
        Instant to = from.plus(Duration.ofMinutes(1));

        List<List<Long>> result = inRolledBackTransaction(() -> {
            Long 시작경계 = realtimeRoom(PartyStatus.COMPLETED, from);
            Long 구간안 = realtimeRoom(PartyStatus.COMPLETED, from.plusSeconds(59));
            Long 예약 = reservedRoom(PartyStatus.ACTIVE, from.plusSeconds(30));
            Long 끝경계 = realtimeRoom(PartyStatus.COMPLETED, to);
            Long 구간전 = realtimeRoom(PartyStatus.COMPLETED, from.minusNanos(1_000));
            Long 확정전 = realtimeRoom(PartyStatus.ACTIVE, null);
            Long 끝난방 = realtimeRoom(PartyStatus.FINISHED, from.plusSeconds(10));
            Long 취소된방 = reservedRoom(PartyStatus.CANCELED, from.plusSeconds(10));

            return List.of(parties.findOpenConfirmedIdsDepartingBetween(from, to),
                    List.of(시작경계, 구간안, 예약), List.of(끝경계, 구간전, 확정전, 끝난방, 취소된방));
        });

        assertThat(result.get(0)).containsAll(result.get(1)).doesNotContainAnyElementsOf(result.get(2));
    }

    @Test
    void 출발_전_알림_대상은_같은_구간의_열린_예약_방만이다() {
        Instant from = Instant.now().plus(Duration.ofHours(3)).truncatedTo(ChronoUnit.MINUTES);
        Instant to = from.plus(Duration.ofMinutes(1));

        List<List<Long>> result = inRolledBackTransaction(() -> {
            Long 예약 = reservedRoom(PartyStatus.ACTIVE, from.plusSeconds(30));
            Long 확정한_실시간 = realtimeRoom(PartyStatus.COMPLETED, from.plusSeconds(30));
            Long 취소된_예약 = reservedRoom(PartyStatus.CANCELED, from.plusSeconds(30));
            Long 구간밖_예약 = reservedRoom(PartyStatus.ACTIVE, to);

            return List.of(parties.findOpenReservationIdsDepartingBetween(from, to),
                    List.of(예약), List.of(확정한_실시간, 취소된_예약, 구간밖_예약));
        });

        assertThat(result.get(0)).containsAll(result.get(1)).doesNotContainAnyElementsOf(result.get(2));
    }

    @Test
    void 출발_재촉_구간은_스케줄러처럼_departure_at_을_재촉_간격만큼_당긴_구간으로_고른다() {
        Instant to = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant from = to.minus(Duration.ofMinutes(1));

        List<List<Long>> result = inRolledBackTransaction(() -> {
            List<Long> due = new ArrayList<>();
            List<Long> notDue = new ArrayList<>();
            for(Duration offset : Party.DEPARTURE_REMINDER_OFFSETS) {
                due.add(realtimeRoom(PartyStatus.COMPLETED, from.minus(offset).plusSeconds(20)));
            }
            notDue.add(realtimeRoom(PartyStatus.COMPLETED, from.minus(Duration.ofMinutes(45))));   // 30분과 1시간 차례 사이
            notDue.add(realtimeRoom(PartyStatus.COMPLETED, from.minus(Duration.ofHours(5))));      // 마지막(4시간) 차례 뒤

            List<Long> picked = new ArrayList<>();
            for(Duration offset : Party.DEPARTURE_REMINDER_OFFSETS) {
                picked.addAll(parties.findOpenConfirmedIdsDepartingBetween(from.minus(offset), to.minus(offset)));
            }

            return List.of(picked, due, notDue);
        });

        assertThat(result.get(0)).containsAll(result.get(1)).doesNotContainAnyElementsOf(result.get(2));
    }

    // ───────────────────────── 보내기 ─────────────────────────

    @Test
    void 알림을_보내도_방은_바뀌지_않는다() {
        // 실제 스케줄러가 고르지 않게 약속을 3시간 뒤로 둔다
        Instant meetingAt = Instant.now().plus(Duration.ofHours(3)).truncatedTo(ChronoUnit.MICROS);
        Party party = Party.reserve(방장, DEPARTURE, DESTINATION, "제주공항", "서귀포", new Capacity(3), Instant.now(), meetingAt);
        party.join(동승자, Instant.now());
        Long id = parties.save(party).getId();
        created.add(id);
        Long version = jdbc.queryForObject("SELECT version FROM match_room WHERE id = ?", Long.class, id);

        reminderService.remind(id);
        notificationService.notifyMeetingTime(id);
        notificationService.remindDeparture(id, meetingAt.plus(Duration.ofMinutes(30)));

        assertThat(jdbc.queryForObject("SELECT version FROM match_room WHERE id = ?", Long.class, id)).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT status FROM match_room WHERE id = ?", String.class, id)).isEqualTo("ACTIVE");
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
        Party party = Party.create(방장, DEPARTURE, DESTINATION, "제주공항", "서귀포", new Capacity(3), Instant.now(),
                30000, 60, "_p~iF~ps|U_ulLnnqC");
        party.join(동승자, Instant.now());
        Long id = parties.save(party).getId();
        jdbc.update("UPDATE match_room SET status = ?, departure_at = ? WHERE id = ?", status.name(), departureAt == null ? null : at(departureAt), id);
        return id;
    }

    /** 방장·동승자 둘이 있는 예약 방 - 출발 시각만 departureAt 으로 옮긴다 */
    private Long reservedRoom(PartyStatus status, Instant departureAt) {
        Party party = Party.reserve(방장, DEPARTURE, DESTINATION, "제주공항", "서귀포", new Capacity(3), Instant.now(), Instant.now().plus(Duration.ofHours(1)));
        party.join(동승자, Instant.now());
        Long id = parties.save(party).getId();
        jdbc.update("UPDATE match_room SET status = ?, departure_at = ? WHERE id = ?", status.name(), at(departureAt), id);
        return id;
    }

    private static OffsetDateTime at(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
