package team.codingforest.moyeota.matching.noshow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.noshow.domain.NoShowHistory;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionStatus;
import team.codingforest.moyeota.matching.noshow.domain.ObjectionSummary;
import team.codingforest.moyeota.matching.party.PartyService;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.dto.OpenReservationCommand;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 *  출발·자동 정리로 생긴 노쇼가 같은 트랜잭션에서 user_no_show 와 users 행의 제한으로 남고, 제한 중엔 방 만들기·참여가 막히며,
 *  이의가 인정되면 노쇼가 무효로 돌아가고 제재가 풀리는 흐름을 실제 서비스와 Postgres 로 본다
 */
@SpringBootTest(properties = "spring.profiles.active=dev")
class NoShowSanctionFlowPostgresTest {
    // 다른 테스트가 남긴 방과 섞이지 않게 제주 좌표를 쓴다
    private static final double DEPARTURE_LAT = 33.4996;
    private static final double DEPARTURE_LNG = 126.5312;
    private static final Location DEPARTURE = new Location(DEPARTURE_LAT, DEPARTURE_LNG);
    private static final Location DESTINATION = new Location(33.5104, 126.4914);
    private static final Duration DAY = Duration.ofHours(24);

    @Autowired PartyService partyService;
    @Autowired NoShowObjectionService objectionService;
    @Autowired Parties parties;
    @Autowired JdbcTemplate jdbc;

    private final List<Long> rooms = new ArrayList<>();
    private final List<Long> users = new ArrayList<>();
    private Long 방장;
    private Long 동승자;
    private Long 늦은사람;
    private Long 다른방장;

    @BeforeEach
    void setUp() {
        방장 = newUser();
        동승자 = newUser();
        늦은사람 = newUser();
        다른방장 = newUser();
    }

    @AfterEach
    void tearDown() {
        rooms.forEach(id -> {
            jdbc.update("DELETE FROM chat_room_user WHERE chat_room_id IN (SELECT id FROM chat_room WHERE party_id = ?)", id);
            jdbc.update("DELETE FROM chat_room WHERE party_id = ?", id);
            jdbc.update("DELETE FROM no_show_objection WHERE match_id = ?", id);
            jdbc.update("DELETE FROM ride_record WHERE match_id = ?", id);
            jdbc.update("DELETE FROM user_match_room WHERE match_id = ?", id);
            jdbc.update("DELETE FROM match_room WHERE id = ?", id);
        });
        users.forEach(id -> {
            jdbc.update("DELETE FROM user_no_show WHERE user_id = ?", id);
            jdbc.update("DELETE FROM users WHERE id = ?", id);
        });
    }

    @Test
    void 출발로_노쇼가_되면_제재가_쌓여_참여가_막히고_이의가_인정되면_노쇼가_무효로_돌아가며_풀린다() {
        Long id = confirmedRoomOfThree();
        Instant meetingAt = parties.findById(id).orElseThrow().getDepartureAt();
        partyService.arrive(id, 방장, DEPARTURE_LAT, DEPARTURE_LNG);
        partyService.depart(id, 방장);   // 동승자·늦은사람 노쇼

        assertThat(count(늦은사람)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT meeting_at FROM user_no_show WHERE match_id = ? AND user_id = ?", Timestamp.class, id, 늦은사람).toInstant())
                .as("노쇼 기록에 방의 약속 시간이 남는다").isEqualTo(meetingAt);
        assertThat(restrictedUntil(늦은사람)).as("기록 시각이 아니라 약속 시간부터 24시간").isEqualTo(meetingAt.plus(DAY));
        assertThat(count(동승자)).isEqualTo(1);
        assertThat(count(방장)).as("도착한 사람은 제재 없음").isZero();
        assertThat(restrictedUntil(방장)).isNull();

        Long 다른방 = openRoom(다른방장);
        assertFails(() -> partyService.join(다른방, 늦은사람), MatchingErrorCode.NO_SHOW_RESTRICTED);
        assertFails(() -> partyService.reserve(reservation(늦은사람)), MatchingErrorCode.NO_SHOW_RESTRICTED);
        assertThat(parties.findById(다른방).orElseThrow().getMembers()).hasSize(1);

        NoShowHistory before = onlyHistory(늦은사람);
        assertThat(before.partyId()).isEqualTo(id);
        assertThat(before.departure()).isEqualTo("제주공항");
        assertThat(before.destination()).isEqualTo("제주시청");
        assertThat(before.meetingAt()).isEqualTo(meetingAt);
        assertThat(before.isVoided()).isFalse();
        assertThat(before.objectionStatus()).isNull();
        assertThat(objectionService.findMyNoShows(방장)).isEmpty();

        objectionService.object(id, 늦은사람, "  버스가 늦게 와서 도착 버튼을 못 눌렀습니다 ");
        assertFails(() -> objectionService.object(id, 늦은사람, "다시 냅니다"), MatchingErrorCode.ALREADY_OBJECTED);
        assertFails(() -> objectionService.object(id, 방장, "저는 탔습니다"), MatchingErrorCode.NOT_NO_SHOW);
        assertThat(onlyHistory(늦은사람).objectionStatus()).isEqualTo(ObjectionStatus.PENDING);

        ObjectionSummary pending = pendingObjectionOf(id);
        assertThat(pending.userId()).isEqualTo(늦은사람);
        assertThat(pending.reason()).isEqualTo("버스가 늦게 와서 도착 버튼을 못 눌렀습니다");
        assertThat(pending.meetingAt()).isEqualTo(meetingAt);
        assertThat(pending.departure()).isEqualTo("제주공항");

        objectionService.accept(pending.id());

        assertThat(voidedAt(id, 늦은사람)).isNotNull();
        assertThat(noShowVoidedAt(id, 늦은사람)).as("user 모듈의 노쇼도 같은 시각에 무효").isEqualTo(voidedAt(id, 늦은사람));
        assertThat(voidedAt(id, 동승자)).as("이의를 내지 않은 노쇼는 그대로").isNull();
        assertThat(noShowVoidedAt(id, 동승자)).isNull();
        assertThat(count(늦은사람)).isZero();
        assertThat(restrictedUntil(늦은사람)).isNull();
        assertThat(count(동승자)).isEqualTo(1);
        assertThat(objectionStatus(pending.id())).isEqualTo("ACCEPTED");
        assertThat(resolvedAt(pending.id())).isNotNull();

        NoShowHistory after = onlyHistory(늦은사람);
        assertThat(after.isVoided()).isTrue();
        assertThat(after.objectionStatus()).isEqualTo(ObjectionStatus.ACCEPTED);

        assertFails(() -> objectionService.accept(pending.id()), MatchingErrorCode.OBJECTION_ALREADY_RESOLVED);
        assertFails(() -> objectionService.reject(pending.id()), MatchingErrorCode.OBJECTION_ALREADY_RESOLVED);
        assertFails(() -> objectionService.object(id, 늦은사람, "한 번 더"), MatchingErrorCode.NOT_NO_SHOW);

        partyService.join(다른방, 늦은사람);
        assertThat(parties.findById(다른방).orElseThrow().hasMember(늦은사람)).as("제한이 풀려 다시 참여할 수 있다").isTrue();
        assertFails(() -> partyService.join(다른방, 동승자), MatchingErrorCode.NO_SHOW_RESTRICTED);

        // 참여 이벤트로 채팅방이 비동기로 열린다 - 방을 지우기 전에 리스너가 끝나기를 기다린다(끝나지 않으면 미완료 발행 기록이 남는다)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM chat_room_user cu JOIN chat_room c ON c.id = cu.chat_room_id WHERE c.party_id = ? AND cu.user_id = ?",
                Integer.class, 다른방, 늦은사람)).isEqualTo(1));
    }

    @Test
    void 이의를_거절하면_노쇼와_제재가_그대로다() {
        Long id = confirmedRoomOfThree();
        partyService.arrive(id, 방장, DEPARTURE_LAT, DEPARTURE_LNG);
        partyService.depart(id, 방장);
        Instant until = restrictedUntil(늦은사람);

        objectionService.object(id, 늦은사람, "억울합니다");
        Long objectionId = pendingObjectionOf(id).id();
        objectionService.reject(objectionId);

        assertThat(objectionStatus(objectionId)).isEqualTo("REJECTED");
        assertThat(voidedAt(id, 늦은사람)).isNull();
        assertThat(count(늦은사람)).isEqualTo(1);
        assertThat(restrictedUntil(늦은사람)).isEqualTo(until);
        assertThat(onlyHistory(늦은사람).objectionStatus()).isEqualTo(ObjectionStatus.REJECTED);
        assertFails(() -> objectionService.accept(objectionId), MatchingErrorCode.OBJECTION_ALREADY_RESOLVED);
        assertFails(() -> objectionService.object(id, 늦은사람, "다시"), MatchingErrorCode.ALREADY_OBJECTED);
    }

    @Test
    void 자동_정리의_노쇼도_약속_시간부터_제재가_매겨지고_노쇼가_쌓이면_제한이_노쇼_횟수만큼_길어진다() {
        Long first = confirmedRoomOfThree();
        Instant firstMeetingAt = parties.findById(first).orElseThrow().getDepartureAt();
        partyService.autoClose(first);   // 아무도 도착하지 않음 - 전원 노쇼, CANCELED

        assertThat(parties.findById(first).orElseThrow().getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(count(방장)).isEqualTo(1);
        assertThat(restrictedUntil(방장)).isEqualTo(firstMeetingAt.plus(DAY));

        // 제한 전에 들어가 있던 다른 약속 방에서 또 노쇼 - 2건, 그 방의 약속 시간부터 48시간
        Long second = confirmedRoom(방장, 다른방장);
        Instant secondMeetingAt = parties.findById(second).orElseThrow().getDepartureAt();
        partyService.arrive(second, 다른방장, DEPARTURE_LAT, DEPARTURE_LNG);
        partyService.depart(second, 다른방장);

        assertThat(count(방장)).isEqualTo(2);
        assertThat(restrictedUntil(방장)).isEqualTo(secondMeetingAt.plus(DAY.multipliedBy(2)));
        assertThat(objectionService.findMyNoShows(방장)).extracting(NoShowHistory::partyId).as("노쇼 시각 최신순").containsExactly(second, first);
    }

    @Test
    void 노쇼한_사용자는_실시간_방도_만들_수_없다() {
        Long id = confirmedRoomOfThree();
        partyService.arrive(id, 방장, DEPARTURE_LAT, DEPARTURE_LNG);
        partyService.depart(id, 방장);

        assertThatThrownBy(() -> partyService.open(new team.codingforest.moyeota.matching.party.dto.OpenPartyCommand(
                늦은사람, DEPARTURE_LAT, DEPARTURE_LNG, 33.5104, 126.4914, "제주공항", "제주시청", 3)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MatchingErrorCode.NO_SHOW_RESTRICTED);
    }

    // ───────────────────────── 도우미 ─────────────────────────

    private Long newUser() {
        Long id = jdbc.queryForObject("INSERT INTO users (public_id, login_type, created_at) VALUES (?, 'LOCAL', now()) RETURNING id",
                Long.class, UUID.randomUUID());
        users.add(id);
        return id;
    }

    /** 방장·동승자·늦은사람 셋이 찬 실시간 방을 방금 지난 시각(30초 전)으로 확정한다 - 바로 도착·출발·자동 정리를 할 수 있다 */
    private Long confirmedRoomOfThree() {
        return confirmedRoom(방장, 동승자, 늦은사람);
    }

    /** members 첫 번째가 방장 */
    private Long confirmedRoom(Long... members) {
        Party party = Party.create(members[0], DEPARTURE, DESTINATION, "제주공항", "제주시청", new Capacity(3), Instant.now(),
                9000, 20, "_p~iF~ps|U_ulLnnqC");
        for (int i = 1; i < members.length; i++) party.join(members[i]);
        Long id = parties.save(party).getId();
        rooms.add(id);

        partyService.confirm(id, members[0], Instant.now().minusSeconds(30));

        return id;
    }

    private Long openRoom(Long host) {
        Party party = Party.create(host, DEPARTURE, DESTINATION, "제주공항", "제주시청", new Capacity(3), Instant.now(),
                9000, 20, "_p~iF~ps|U_ulLnnqC");
        Long id = parties.save(party).getId();
        rooms.add(id);
        return id;
    }

    private OpenReservationCommand reservation(Long creatorId) {
        return new OpenReservationCommand(creatorId, DEPARTURE_LAT, DEPARTURE_LNG, 33.5104, 126.4914, "제주공항", "제주시청", 3,
                Instant.now().plus(Duration.ofDays(1)));
    }

    private NoShowHistory onlyHistory(Long userId) {
        List<NoShowHistory> histories = objectionService.findMyNoShows(userId);
        assertThat(histories).hasSize(1);
        return histories.getFirst();
    }

    private ObjectionSummary pendingObjectionOf(Long partyId) {
        return objectionService.findObjections(ObjectionStatus.PENDING).stream()
                .filter(o -> o.partyId().equals(partyId))
                .findFirst()
                .orElseThrow();
    }

    /** 지금 세는 노쇼 수 - 무효가 아니고 약속 시간이 30일 안 */
    private int count(Long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM user_no_show WHERE user_id = ? AND voided_at IS NULL AND meeting_at > now() - interval '30 days'",
                Integer.class, userId);
    }

    private Instant restrictedUntil(Long userId) {
        return instant(jdbc.queryForObject("SELECT restricted_until FROM users WHERE id = ?", Timestamp.class, userId));
    }

    private Instant voidedAt(Long partyId, Long userId) {
        return instant(jdbc.queryForObject("SELECT no_show_voided_at FROM user_match_room WHERE match_id = ? AND user_id = ?",
                Timestamp.class, partyId, userId));
    }

    private Instant noShowVoidedAt(Long partyId, Long userId) {
        return instant(jdbc.queryForObject("SELECT voided_at FROM user_no_show WHERE match_id = ? AND user_id = ?",
                Timestamp.class, partyId, userId));
    }

    private String objectionStatus(Long objectionId) {
        return jdbc.queryForObject("SELECT status FROM no_show_objection WHERE id = ?", String.class, objectionId);
    }

    private Instant resolvedAt(Long objectionId) {
        return instant(jdbc.queryForObject("SELECT resolved_at FROM no_show_objection WHERE id = ?", Timestamp.class, objectionId));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static void assertFails(Runnable action, MatchingErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(code);
    }
}
