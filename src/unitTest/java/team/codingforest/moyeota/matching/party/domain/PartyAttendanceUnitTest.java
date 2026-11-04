package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 *  출발지 도착(arrive)·출발(depart)과 노쇼 기록, 약속이 정해진 방의 이전 합승 종료 경로 차단
 */
class PartyAttendanceUnitTest {
    private static final Long HOST_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long THIRD_ID = 3L;
    private static final Long OUTSIDER_ID = 99L;
    private static final Location DEPARTURE = new Location(37.4979, 127.0276);
    private static final Location DESTINATION = new Location(37.3948, 127.1112);
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    // 약속 시간이 5분 지났다
    private static final Instant MEETING_AT = NOW.minus(Duration.ofMinutes(5));
    private static final Instant ARRIVED_AT = MEETING_AT.plusSeconds(30);
    // 출발지 100m 안인지는 서비스가 DB(PostGIS)에 물어 넘긴다 - 도메인은 그 답만 받는다. 거리 경계는 Postgres 통합 테스트가 본다
    private static final boolean NEAR = true;
    private static final boolean FAR = false;

    // ───────────────────────── arrive ─────────────────────────

    @DisplayName("arrive - 약속 시간 뒤 출발지 100m 안의 멤버는 도착 시각이 남고 version 이 1 오른다")
    @Test
    void arrive() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        boolean arrived = party.arrive(MEMBER_ID, NEAR, NOW);

        /*
         * verify
         */
        assertThat(arrived).isTrue();
        assertThat(memberOf(party, MEMBER_ID).isArrived()).isTrue();
        assertThat(memberOf(party, MEMBER_ID).getArrivedAt()).isEqualTo(NOW);
        assertThat(memberOf(party, HOST_ID).isArrived()).as("다른 멤버는 그대로").isFalse();
        assertThat(party.getVersion()).isEqualTo(1L);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
    }

    @DisplayName("arrive - 약속 시간 정각이면 받는다")
    @Test
    void arrive_meetingTimeBoundary() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        boolean arrived = party.arrive(HOST_ID, NEAR, MEETING_AT);

        /*
         * verify
         */
        assertThat(arrived).isTrue();
        assertThat(memberOf(party, HOST_ID).getArrivedAt()).isEqualTo(MEETING_AT);
    }

    @DisplayName("arrive - 이미 도착한 멤버가 다시 누르면 오류 없이 처음 시각을 그대로 두고 version 도 그대로다")
    @Test
    void arrive_idempotent() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, member(HOST_ID), member(MEMBER_ID));
        party.arrive(MEMBER_ID, NEAR, NOW);
        long version = party.getVersion();

        /*
         * test
         */
        // 그새 멀어져 출발지 밖이라는 답이 와도 다시 보지 않는다
        boolean arrivedAgain = party.arrive(MEMBER_ID, FAR, NOW.plus(Duration.ofMinutes(3)));

        /*
         * verify
         */
        assertThat(arrivedAgain).isFalse();
        assertThat(memberOf(party, MEMBER_ID).getArrivedAt()).isEqualTo(NOW);
        assertThat(party.getVersion()).isEqualTo(version);
    }

    @DisplayName("arrive - 저장에서 복원한 도착 멤버도 다시 누르면 처음 시각을 그대로 둔다")
    @Test
    void arrive_idempotentAfterLoad() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, arrivedMember(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        boolean arrivedAgain = party.arrive(HOST_ID, NEAR, NOW);

        /*
         * verify
         */
        assertThat(arrivedAgain).isFalse();
        assertThat(memberOf(party, HOST_ID).getArrivedAt()).isEqualTo(ARRIVED_AT);
        assertThat(party.getVersion()).isZero();
    }

    @DisplayName("arrive - 방에 없는 사람이면 NOT_PARTY_MEMBER")
    @Test
    void arrive_notMember() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.arrive(OUTSIDER_ID, NEAR, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_PARTY_MEMBER);
        assertThat(party.getVersion()).isZero();
    }

    @DisplayName("arrive - 약속 시간을 확정하지 않은 실시간 방이면 PARTY_NOT_CONFIRMED")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"ACTIVE", "COMPLETED"})
    void arrive_notConfirmed(PartyStatus status) {

        /*
         * settings
         */
        Party party = realtime(status, null, null, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.arrive(HOST_ID, NEAR, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_CONFIRMED);
        assertThat(memberOf(party, HOST_ID).isArrived()).isFalse();
    }

    @DisplayName("arrive - 약속 시간 1초 전이면 MEETING_NOT_STARTED 이고 도착이 남지 않는다")
    @Test
    void arrive_beforeMeeting() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.arrive(HOST_ID, NEAR, MEETING_AT.minusSeconds(1)));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.MEETING_NOT_STARTED);
        assertThat(memberOf(party, HOST_ID).isArrived()).isFalse();
        assertThat(party.getVersion()).isZero();
    }

    @DisplayName("arrive - 약속 시간 전이면 출발지 밖이라는 답이어도 MEETING_NOT_STARTED 가 먼저다")
    @Test
    void arrive_beforeMeetingAndFar() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.arrive(HOST_ID, FAR, MEETING_AT.minusSeconds(1)));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.MEETING_NOT_STARTED);
    }

    @DisplayName("arrive - 출발지 100m 밖이라는 답이면 TOO_FAR_FROM_DEPARTURE 이고 도착이 남지 않는다")
    @Test
    void arrive_tooFar() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.arrive(HOST_ID, FAR, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.TOO_FAR_FROM_DEPARTURE);
        assertThat(memberOf(party, HOST_ID).isArrived()).isFalse();
        assertThat(party.getVersion()).isZero();
    }

    @DisplayName("arrive - 닫혔거나 기사 호출 단계로 넘어간 방이면 PARTY_CLOSED")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"MATCHING", "DRIVER_ASSIGNED", "IN_RIDE", "FINISHED", "CANCELED"})
    void arrive_closed(PartyStatus status) {

        /*
         * settings
         */
        Party party = confirmedRealtime(status, null, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.arrive(HOST_ID, NEAR, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_CLOSED);
        assertThat(memberOf(party, HOST_ID).isArrived()).isFalse();
    }

    @DisplayName("arrive - 끝난 방에서는 이미 도착한 멤버가 다시 눌러도 PARTY_CLOSED 다")
    @Test
    void arrive_closedEvenIfArrived() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.FINISHED, null, arrivedMember(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.arrive(HOST_ID, NEAR, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_CLOSED);
    }

    @DisplayName("arrive - 예약 방은 출발 시각이 지나도 모집 정리 전이라 ACTIVE 일 수 있고, 정원이 차 COMPLETED 여도 받는다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"ACTIVE", "COMPLETED"})
    void arrive_reserved(PartyStatus status) {

        /*
         * settings
         */
        Party party = reserved(status, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        boolean arrived = party.arrive(MEMBER_ID, NEAR, NOW);

        /*
         * verify
         */
        assertThat(arrived).isTrue();
        assertThat(memberOf(party, MEMBER_ID).getArrivedAt()).isEqualTo(NOW);
        assertThat(party.getStatus()).isEqualTo(status);
    }

    @DisplayName("arrive - 예약 방도 출발 시각 전이면 MEETING_NOT_STARTED")
    @Test
    void arrive_reservedBeforeDeparture() {

        /*
         * settings
         */
        Party party = reserved(PartyStatus.ACTIVE, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.arrive(HOST_ID, NEAR, MEETING_AT.minus(Duration.ofMinutes(10))));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.MEETING_NOT_STARTED);
    }

    // ───────────────────────── depart ─────────────────────────

    @DisplayName("depart - 도착한 멤버가 누르면 방이 FINISHED 로 끝나고 도착하지 않은 멤버만 노쇼로 찍혀 돌아온다")
    @Test
    void depart() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, arrivedMember(HOST_ID), arrivedMember(MEMBER_ID), member(THIRD_ID));

        /*
         * test
         */
        List<Long> noShows = party.depart(HOST_ID, NOW);

        /*
         * verify
         */
        assertThat(noShows).containsExactly(THIRD_ID);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.getVersion()).isEqualTo(1L);
        assertThat(memberOf(party, THIRD_ID).isNoShow()).isTrue();
        assertThat(memberOf(party, THIRD_ID).getNoShowAt()).isEqualTo(NOW);
        assertThat(memberOf(party, HOST_ID).isNoShow()).isFalse();
        assertThat(memberOf(party, HOST_ID).getNoShowAt()).isNull();
        assertThat(memberOf(party, MEMBER_ID).isNoShow()).isFalse();
        assertThat(party.getMembers()).as("노쇼도 멤버로 남는다").hasSize(3);
    }

    @DisplayName("depart - 방장이 아니어도 도착했으면 출발할 수 있고, 도착하지 않은 방장은 노쇼다. 노쇼 목록은 합류 순이다")
    @Test
    void depart_byNonHost() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, member(HOST_ID), member(MEMBER_ID), arrivedMember(THIRD_ID));

        /*
         * test
         */
        List<Long> noShows = party.depart(THIRD_ID, NOW);

        /*
         * verify
         */
        assertThat(noShows).containsExactly(HOST_ID, MEMBER_ID);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(memberOf(party, HOST_ID).isNoShow()).isTrue();
        assertThat(memberOf(party, MEMBER_ID).isNoShow()).isTrue();
        assertThat(memberOf(party, THIRD_ID).isNoShow()).isFalse();
    }

    @DisplayName("depart - 모두 도착했으면 노쇼가 없다")
    @Test
    void depart_everyoneArrived() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, arrivedMember(HOST_ID), arrivedMember(MEMBER_ID));

        /*
         * test
         */
        List<Long> noShows = party.depart(MEMBER_ID, NOW);

        /*
         * verify
         */
        assertThat(noShows).isEmpty();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.getMembers()).noneMatch(PartyMember::isNoShow);
    }

    @DisplayName("depart - 약속 시간 정각에 누를 수 있다")
    @Test
    void depart_atMeetingTime() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, arrivedMember(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        List<Long> noShows = party.depart(HOST_ID, MEETING_AT);

        /*
         * verify
         */
        assertThat(noShows).containsExactly(MEMBER_ID);
        assertThat(memberOf(party, MEMBER_ID).getNoShowAt()).isEqualTo(MEETING_AT);
    }

    @DisplayName("depart - 누른 사람이 아직 도착 전이면 NOT_ARRIVED 이고 방과 멤버가 그대로다")
    @Test
    void depart_notArrived() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, arrivedMember(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.depart(MEMBER_ID, NOW));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.NOT_ARRIVED, party, PartyStatus.COMPLETED);
    }

    @DisplayName("depart - 약속 시간 전이면 MEETING_NOT_STARTED 이고 방과 멤버가 그대로다")
    @Test
    void depart_beforeMeeting() {

        /*
         * settings
         */
        // 도착 시각이 있는 멤버로 복원해 시간 검사만 걸리게 한다
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, arrivedMember(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.depart(HOST_ID, MEETING_AT.minusSeconds(1)));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.MEETING_NOT_STARTED, party, PartyStatus.COMPLETED);
    }

    @DisplayName("depart - 약속 시간을 확정하지 않은 실시간 방이면 PARTY_NOT_CONFIRMED")
    @Test
    void depart_notConfirmed() {

        /*
         * settings
         */
        Party party = realtime(PartyStatus.COMPLETED, null, null, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.depart(HOST_ID, NOW));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.PARTY_NOT_CONFIRMED, party, PartyStatus.COMPLETED);
    }

    @DisplayName("depart - 닫혔거나 기사 호출 단계로 넘어간 방이면 PARTY_CLOSED - 먼저 누른 사람이 이미 끝낸 방 포함")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"MATCHING", "DRIVER_ASSIGNED", "IN_RIDE", "FINISHED", "CANCELED"})
    void depart_closed(PartyStatus status) {

        /*
         * settings
         */
        Party party = confirmedRealtime(status, null, arrivedMember(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.depart(HOST_ID, NOW));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.PARTY_CLOSED, party, status);
    }

    @DisplayName("depart - 이미 출발로 끝난 방에서 다시 누르면 PARTY_CLOSED 이고 노쇼가 다시 찍히지 않는다")
    @Test
    void depart_twice() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, arrivedMember(HOST_ID), arrivedMember(MEMBER_ID), member(THIRD_ID));
        party.depart(HOST_ID, NOW);

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.depart(MEMBER_ID, NOW.plusSeconds(10)));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_CLOSED);
        assertThat(memberOf(party, THIRD_ID).getNoShowAt()).isEqualTo(NOW);
        assertThat(party.getVersion()).isEqualTo(1L);
    }

    @DisplayName("depart - 방에 없는 사람이면 NOT_PARTY_MEMBER")
    @Test
    void depart_notMember() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, arrivedMember(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.depart(OUTSIDER_ID, NOW));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.NOT_PARTY_MEMBER, party, PartyStatus.COMPLETED);
    }

    @DisplayName("depart - 아무도 들어오지 않은 예약 방(방장 혼자)은 NOT_ENOUGH_MEMBERS - 모집 정리 때 취소될 방이다")
    @Test
    void depart_soloReservation() {

        /*
         * settings
         */
        Party party = reserved(PartyStatus.ACTIVE, arrivedMember(HOST_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.depart(HOST_ID, NOW));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.NOT_ENOUGH_MEMBERS, party, PartyStatus.ACTIVE);
    }

    @DisplayName("depart - 출발 시각이 지났는데 아직 ACTIVE 인 예약 방도 바로 FINISHED 로 끝난다")
    @Test
    void depart_reservedActive() {

        /*
         * settings
         */
        Party party = reserved(PartyStatus.ACTIVE, member(HOST_ID), arrivedMember(MEMBER_ID));

        /*
         * test
         */
        List<Long> noShows = party.depart(MEMBER_ID, NOW);

        /*
         * verify
         */
        assertThat(noShows).containsExactly(HOST_ID);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
    }

    @DisplayName("depart - 돌려준 노쇼 목록은 바꿀 수 없다")
    @Test
    void depart_returnsUnmodifiableList() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, arrivedMember(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        List<Long> noShows = party.depart(HOST_ID, NOW);
        Throwable thrown = catchThrowable(() -> noShows.add(OUTSIDER_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
    }

    // ───────────────────────── finishWithoutDriver ─────────────────────────

    @DisplayName("finishWithoutDriver - 약속을 확정한 실시간 방은 방장이라도 USE_DEPART_FOR_CONFIRMED_PARTY")
    @Test
    void finish_confirmedRealtime() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, arrivedMember(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.finishWithoutDriver(HOST_ID));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.USE_DEPART_FOR_CONFIRMED_PARTY, party, PartyStatus.COMPLETED);
    }

    @DisplayName("finishWithoutDriver - 약속이 정해진 방에선 방장이 아닌 멤버에게도 NOT_PARTY_HOST 대신 출발로 끝내라고 알린다")
    @Test
    void finish_confirmedByNonHost() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.finishWithoutDriver(MEMBER_ID));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.USE_DEPART_FOR_CONFIRMED_PARTY, party, PartyStatus.COMPLETED);
    }

    @DisplayName("finishWithoutDriver - 예약 방은 출발 시각이 지나도 USE_DEPART_FOR_CONFIRMED_PARTY")
    @Test
    void finish_reserved() {

        /*
         * settings
         */
        Party party = reserved(PartyStatus.COMPLETED, arrivedMember(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.finishWithoutDriver(HOST_ID));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.USE_DEPART_FOR_CONFIRMED_PARTY, party, PartyStatus.COMPLETED);
    }

    @DisplayName("finishWithoutDriver - 방에 없는 사람은 약속이 정해진 방이어도 NOT_PARTY_MEMBER 가 먼저다")
    @Test
    void finish_confirmedNotMember() {

        /*
         * settings
         */
        Party party = confirmedRealtime(PartyStatus.COMPLETED, null, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.finishWithoutDriver(OUTSIDER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_PARTY_MEMBER);
    }

    @DisplayName("finishWithoutDriver - 확정 전 실시간 방은 예전처럼 방장이 끝내고 노쇼 없이 모두 탄 것으로 남는다")
    @Test
    void finish_unconfirmed() {

        /*
         * settings
         */
        Party party = realtime(PartyStatus.COMPLETED, null, null, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        party.finishWithoutDriver(HOST_ID);

        /*
         * verify
         */
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.getVersion()).isEqualTo(1L);
        assertThat(party.getMembers()).noneMatch(PartyMember::isNoShow);
    }

    @DisplayName("finishWithoutDriver - 확정 전 실시간 방에서 방장이 아니면 예전처럼 NOT_PARTY_HOST")
    @Test
    void finish_unconfirmedByNonHost() {

        /*
         * settings
         */
        Party party = realtime(PartyStatus.COMPLETED, null, null, member(HOST_ID), member(MEMBER_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.finishWithoutDriver(MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_PARTY_HOST);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
    }

    // ───────────────────────── 공통 도우미 ─────────────────────────

    private static final List<Long> JOIN_ORDER = List.of(HOST_ID, MEMBER_ID, THIRD_ID, OUTSIDER_ID);

    /** 도착 전 멤버. memberId 순서대로 합류했다 */
    private static PartyMember member(Long memberId) {
        return PartyMember.load(memberId, joinedAt(memberId));
    }

    /** 약속 시간 30초 뒤 도착한 멤버 */
    private static PartyMember arrivedMember(Long memberId) {
        return PartyMember.load(memberId, joinedAt(memberId), ARRIVED_AT, null, null);
    }

    private static Instant joinedAt(Long memberId) {
        return MEETING_AT.minus(Duration.ofMinutes(30)).plusSeconds(JOIN_ORDER.indexOf(memberId));
    }

    /** 방장이 MEETING_AT 으로 약속을 확정한 실시간 방. 첫 멤버가 방장 */
    private static Party confirmedRealtime(PartyStatus status, Long taxiDriverId, PartyMember... members) {
        return realtime(status, MEETING_AT, taxiDriverId, members);
    }

    private static Party realtime(PartyStatus status, Instant departureAt, Long taxiDriverId, PartyMember... members) {
        return Party.load(100L, DEPARTURE, DESTINATION, Radius.DEFAULT, Radius.DEFAULT, "강남역", "판교역", new Capacity(3),
                new ArrayList<>(List.of(members)), members[0].getMemberId(), MEETING_AT.minus(Duration.ofHours(1)), status,
                12000, 25, "encoded-path", taxiDriverId, null, null, 0L, RoomType.REALTIME, departureAt, null);
    }

    /** 출발 시각이 MEETING_AT 인 예약 방. 첫 멤버가 방장 */
    private static Party reserved(PartyStatus status, PartyMember... members) {
        return Party.load(100L, DEPARTURE, DESTINATION, Radius.DEFAULT, Radius.DEFAULT, "강남역", "판교역", new Capacity(3),
                new ArrayList<>(List.of(members)), members[0].getMemberId(), MEETING_AT.minus(Duration.ofDays(1)), status,
                null, null, null, null, null, null, 0L, RoomType.RESERVED, MEETING_AT, null);
    }

    private static PartyMember memberOf(Party party, Long memberId) {
        return party.getMembers().stream()
                .filter(m -> m.getMemberId().equals(memberId))
                .findFirst()
                .orElseThrow();
    }

    /** 거절된 출발·종료는 상태·version·노쇼를 건드리지 않는다 */
    private static void assertRejected(Throwable thrown, ErrorCode expected, Party party, PartyStatus status) {
        assertBusinessException(thrown, expected);
        assertThat(party.getStatus()).isEqualTo(status);
        assertThat(party.getVersion()).isZero();
        assertThat(party.getMembers()).noneMatch(m -> m.getNoShowAt() != null);
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
