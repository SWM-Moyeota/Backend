package team.codingforest.moyeota.matching.noshow.infrastructure;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.matching.noshow.domain.NoShowHistory;
import team.codingforest.moyeota.matching.noshow.domain.NoShowObjection;
import team.codingforest.moyeota.matching.noshow.domain.NoShowObjections;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionStatus;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionSummary;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 *  V27 의 no_show_objection 제약(한 사람·한 방 유니크, 상태 CHECK, 방 FK)·users 제재 컬럼 기본값과
 *  내 노쇼 기록·운영자 목록 JPQL 이 실제 Postgres 에서 맞게 도는지 본다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class NoShowObjectionQueryPostgresTest {
    // 다른 테스트가 남긴 방과 섞이지 않게 대구 좌표를 쓴다
    private static final Location DEPARTURE = new Location(35.8714, 128.6014);
    private static final Location DESTINATION = new Location(35.8563, 128.5942);
    private static final Instant BASE = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(10, ChronoUnit.DAYS);

    @Autowired Parties parties;
    @Autowired NoShowObjections objections;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    // 다른 테스트·이전 실행과 섞이지 않게 매번 새 번호를 쓴다(users 행은 필요 없다 - 조회는 user_match_room 만 본다)
    private final Long 나 = ThreadLocalRandom.current().nextLong(5_000_000_000L, 6_000_000_000L);
    private final Long 남 = 나 + 1;
    private final List<Long> rooms = new ArrayList<>();
    private final List<Long> users = new ArrayList<>();

    @AfterEach
    void tearDown() {
        rooms.forEach(id -> {
            jdbc.update("DELETE FROM no_show_objection WHERE match_id = ?", id);
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match_room WHERE id = ?", id);
        });
        users.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id));
    }

    // ───────────────────────── V27 제약 ─────────────────────────

    @Test
    void 한_사람이_한_방에_이의를_두_번_내면_유니크_제약에_걸리고_다른_사람이나_다른_방은_된다() {
        Long room = room(나, 남);
        Long other = room(나);

        objections.save(NoShowObjection.file(나, room, "첫 신청", Instant.now()));

        assertThatThrownBy(() -> objections.save(NoShowObjection.file(나, room, "두 번째", Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        objections.save(NoShowObjection.file(남, room, "다른 사람", Instant.now()));
        objections.save(NoShowObjection.file(나, other, "다른 방", Instant.now()));

        assertThat(objections.exists(나, room)).isTrue();
        assertThat(objections.exists(남, other)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM no_show_objection WHERE match_id IN (?, ?)", Integer.class, room, other)).isEqualTo(3);
    }

    @Test
    void 상태는_PENDING_ACCEPTED_REJECTED_만_받는다() {
        Long room = room(나);

        assertThatThrownBy(() -> insertObjection(나, room, "DONE"))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertObjection(나, room, "ACCEPTED");
    }

    @Test
    void 없는_방에는_이의를_낼_수_없다() {
        assertThatThrownBy(() -> insertObjection(나, -1L, "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 사유는_500자까지_저장된다() {
        Long room = room(나);

        objections.save(NoShowObjection.file(나, room, "가".repeat(500), Instant.now()));

        assertThatThrownBy(() -> jdbc.update("INSERT INTO no_show_objection (user_id, match_id, reason, status, created_at) VALUES (?, ?, ?, 'PENDING', now())",
                남, room, "가".repeat(501)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void users_의_제재_컬럼은_기존_행에_0과_NULL_로_채워진다() {
        Long id = jdbc.queryForObject("INSERT INTO users (public_id, login_type, created_at) VALUES (?, 'LOCAL', now()) RETURNING id",
                Long.class, UUID.randomUUID());
        users.add(id);

        assertThat(jdbc.queryForObject("SELECT no_show_count FROM users WHERE id = ?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT no_show_count_updated_at FROM users WHERE id = ?", Timestamp.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT restricted_until FROM users WHERE id = ?", Timestamp.class, id)).isNull();
    }

    // ───────────────────────── 내 노쇼 기록 ─────────────────────────

    @Test
    void 내_노쇼_기록은_노쇼로_찍힌_방만_최신순으로_무효_여부와_이의_상태를_싣는다() {
        Long 오래된노쇼 = room(나, 남);          // 이의 없음
        Long 무효된노쇼 = room(나, 남);          // 이의 인정 → 무효
        Long 최근노쇼 = room(나, 남);            // 이의 대기
        Long 탄방 = room(나, 남);                // 나는 도착해 탐, 남이 노쇼
        Long 확정전방 = room(나);                // 노쇼 판정 없음

        meeting(오래된노쇼, BASE);
        meeting(무효된노쇼, BASE.plus(1, ChronoUnit.DAYS));
        meeting(최근노쇼, BASE.plus(2, ChronoUnit.DAYS));
        meeting(탄방, BASE.plus(3, ChronoUnit.DAYS));

        noShow(오래된노쇼, 나, BASE.plusSeconds(600), null);
        noShow(무효된노쇼, 나, BASE.plus(1, ChronoUnit.DAYS).plusSeconds(600), BASE.plus(2, ChronoUnit.DAYS));
        noShow(최근노쇼, 나, BASE.plus(2, ChronoUnit.DAYS).plusSeconds(600), null);
        noShow(탄방, 남, BASE.plus(3, ChronoUnit.DAYS).plusSeconds(600), null);
        noShow(오래된노쇼, 남, BASE.plusSeconds(600), null);

        insertObjection(나, 무효된노쇼, "ACCEPTED");
        insertObjection(나, 최근노쇼, "PENDING");
        insertObjection(남, 오래된노쇼, "REJECTED");   // 남의 이의가 내 줄에 섞이지 않아야 한다

        List<NoShowHistory> mine = objections.findNoShowHistory(나, 20);

        assertThat(mine).extracting(NoShowHistory::partyId, NoShowHistory::meetingAt, NoShowHistory::isVoided, NoShowHistory::objectionStatus)
                .containsExactly(
                        tuple(최근노쇼, BASE.plus(2, ChronoUnit.DAYS), false, ObjectionStatus.PENDING),
                        tuple(무효된노쇼, BASE.plus(1, ChronoUnit.DAYS), true, ObjectionStatus.ACCEPTED),
                        tuple(오래된노쇼, BASE, false, null));
        assertThat(mine.getFirst().departure()).isEqualTo("동대구역");
        assertThat(mine.getFirst().destination()).isEqualTo("반월당");
        assertThat(mine.getFirst().noShowAt()).isEqualTo(BASE.plus(2, ChronoUnit.DAYS).plusSeconds(600));
        assertThat(mine.get(1).noShowVoidedAt()).isEqualTo(BASE.plus(2, ChronoUnit.DAYS));

        assertThat(objections.findNoShowHistory(나, 2)).extracting(NoShowHistory::partyId).as("개수 제한").containsExactly(최근노쇼, 무효된노쇼);
        assertThat(objections.findNoShowHistory(남, 20)).extracting(NoShowHistory::partyId, NoShowHistory::objectionStatus)
                .containsExactly(tuple(탄방, null), tuple(오래된노쇼, ObjectionStatus.REJECTED));
    }

    // ───────────────────────── 운영자 목록 ─────────────────────────

    @Test
    void 운영자_목록은_상태별로_신청_시각_오래된_순이고_방_정보를_싣는다() {
        Long 첫방 = room(나, 남);
        Long 둘째방 = room(나, 남);
        meeting(첫방, BASE);
        meeting(둘째방, BASE.plus(1, ChronoUnit.DAYS));

        Long 늦은신청 = objections.save(NoShowObjection.file(나, 첫방, "늦게 낸 신청", BASE.plus(5, ChronoUnit.DAYS))).getId();
        Long 이른신청 = objections.save(NoShowObjection.file(나, 둘째방, "먼저 낸 신청", BASE.plus(4, ChronoUnit.DAYS))).getId();
        NoShowObjection 처리된 = objections.save(NoShowObjection.file(남, 첫방, "처리된 신청", BASE.plus(3, ChronoUnit.DAYS)));
        // 운영자 처리와 같은 길 - 잠가 읽고 바꾼 뒤 더티체킹으로 저장
        tx.executeWithoutResult(status -> {
            NoShowObjection locked = objections.findByIdForUpdate(처리된.getId()).orElseThrow();
            locked.reject(BASE.plus(6, ChronoUnit.DAYS));
            objections.save(locked);
        });

        List<ObjectionSummary> pending = objections.findByStatus(ObjectionStatus.PENDING, 100).stream()
                .filter(o -> rooms.contains(o.partyId()))
                .toList();

        assertThat(pending).extracting(ObjectionSummary::id).containsExactly(이른신청, 늦은신청);
        ObjectionSummary first = pending.getFirst();
        assertThat(first.userId()).isEqualTo(나);
        assertThat(first.partyId()).isEqualTo(둘째방);
        assertThat(first.departure()).isEqualTo("동대구역");
        assertThat(first.destination()).isEqualTo("반월당");
        assertThat(first.meetingAt()).isEqualTo(BASE.plus(1, ChronoUnit.DAYS));
        assertThat(first.reason()).isEqualTo("먼저 낸 신청");
        assertThat(first.status()).isEqualTo(ObjectionStatus.PENDING);
        assertThat(first.createdAt()).isEqualTo(BASE.plus(4, ChronoUnit.DAYS));
        assertThat(first.resolvedAt()).isNull();

        List<ObjectionSummary> rejected = objections.findByStatus(ObjectionStatus.REJECTED, 100).stream()
                .filter(o -> rooms.contains(o.partyId()))
                .toList();
        assertThat(rejected).extracting(ObjectionSummary::id, ObjectionSummary::resolvedAt)
                .containsExactly(tuple(처리된.getId(), BASE.plus(6, ChronoUnit.DAYS)));
    }

    // ───────────────────────── 도우미 ─────────────────────────

    /** members 첫 번째가 방장인 실시간 방 */
    private Long room(Long... members) {
        Party party = Party.open(members[0], DEPARTURE, DESTINATION, "동대구역", "반월당", new Capacity(3), Instant.now(),
                9000, 20, "_p~iF~ps|U_ulLnnqC");
        for (int i = 1; i < members.length; i++) party.join(members[i]);
        Long id = parties.save(party).getId();
        rooms.add(id);
        return id;
    }

    private void meeting(Long partyId, Instant at) {
        jdbc.update("UPDATE match_room SET departure_at = ?, status = 'FINISHED' WHERE id = ?", Timestamp.from(at), partyId);
    }

    private void noShow(Long partyId, Long userId, Instant at, Instant voidedAt) {
        jdbc.update("UPDATE user_match_room SET no_show_at = ?, no_show_voided_at = ? WHERE match_id = ? AND user_id = ?",
                Timestamp.from(at), voidedAt == null ? null : Timestamp.from(voidedAt), partyId, userId);
    }

    private void insertObjection(Long userId, Long partyId, String status) {
        jdbc.update("INSERT INTO no_show_objection (user_id, match_id, reason, status, created_at) VALUES (?, ?, '사유', ?, now())",
                userId, partyId, status);
    }
}
