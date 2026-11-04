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
 *  약속 시간 확정과 확정된 방 나가기 금지 규칙
 */
class PartyConfirmUnitTest {
    private static final Long HOST_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long THIRD_ID = 3L;
    private static final Long OUTSIDER_ID = 99L;
    private static final Location DEPARTURE = new Location(37.4979, 127.0276);
    private static final Location DESTINATION = new Location(37.3948, 127.1112);
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    private static final Instant MEETING_AT = NOW.plus(Duration.ofMinutes(30));

    // ───────────────────────── confirm ─────────────────────────

    @DisplayName("confirm - 모집 중(ACTIVE)인 방에서 확정하면 약속 시간이 담기고 COMPLETED 로 모집이 끝난다")
    @Test
    void confirm_fromActive() {

        /*
         * settings
         */
        Party party = open(3, MEMBER_ID);
        long before = party.getVersion();

        /*
         * test
         */
        party.confirm(HOST_ID, MEETING_AT, NOW);

        /*
         * verify
         */
        assertThat(party.getDepartureAt()).isEqualTo(MEETING_AT);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(party.getCompletedAt()).isNotNull();
        assertThat(party.getVersion()).isEqualTo(before + 1);
        assertThat(party.isConfirmed()).isTrue();
    }

    @DisplayName("confirm - 정원이 차 이미 COMPLETED 인 방은 상태와 정원 충족 시각을 그대로 두고 약속 시간만 담는다")
    @Test
    void confirm_fromCompleted() {

        /*
         * settings
         */
        Party party = open(2, MEMBER_ID);
        Instant completedAt = party.getCompletedAt();
        long before = party.getVersion();

        /*
         * test
         */
        party.confirm(HOST_ID, MEETING_AT, NOW);

        /*
         * verify
         */
        assertThat(party.getDepartureAt()).isEqualTo(MEETING_AT);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(party.getCompletedAt()).isEqualTo(completedAt);
        assertThat(party.getVersion()).isEqualTo(before + 1);
    }

    @DisplayName("confirm - 약속 시간 경계값인 지금과 정확히 1시간 뒤는 허용한다")
    @Test
    void confirm_boundaries() {

        /*
         * settings
         */
        Party rightNow = open(3, MEMBER_ID);
        Party inAnHour = open(3, MEMBER_ID);

        /*
         * test
         */
        rightNow.confirm(HOST_ID, NOW, NOW);
        inAnHour.confirm(HOST_ID, NOW.plus(Duration.ofHours(1)), NOW);

        /*
         * verify
         */
        assertThat(rightNow.getDepartureAt()).isEqualTo(NOW);
        assertThat(inAnHour.getDepartureAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @DisplayName("confirm - 요청이 오는 동안 지난 1분까지는 약속 시간으로 받는다")
    @Test
    void confirm_clockSkew() {

        /*
         * settings
         */
        Party party = open(3, MEMBER_ID);

        /*
         * test
         */
        party.confirm(HOST_ID, NOW.minus(Duration.ofMinutes(1)), NOW);

        /*
         * verify
         */
        assertThat(party.getDepartureAt()).isEqualTo(NOW.minus(Duration.ofMinutes(1)));
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
    }

    @DisplayName("confirm - 1분 넘게 지난 시각이면 INVALID_MEETING_TIME 이고 방은 그대로다")
    @Test
    void confirm_past() {

        /*
         * settings
         */
        Party party = open(3, MEMBER_ID);

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, NOW.minus(Duration.ofMinutes(1)).minusSeconds(1), NOW));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.INVALID_MEETING_TIME, party, PartyStatus.ACTIVE);
    }

    @DisplayName("confirm - 지금부터 1시간을 넘기면 INVALID_MEETING_TIME 이고 방은 그대로다")
    @Test
    void confirm_overAnHour() {

        /*
         * settings
         */
        Party party = open(3, MEMBER_ID);

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, NOW.plus(Duration.ofHours(1)).plusSeconds(1), NOW));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.INVALID_MEETING_TIME, party, PartyStatus.ACTIVE);
    }

    @DisplayName("confirm - 약속 시간이 null 이면 INVALID_MEETING_TIME")
    @Test
    void confirm_nullTime() {

        /*
         * settings
         */
        Party party = open(3, MEMBER_ID);

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, null, NOW));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.INVALID_MEETING_TIME, party, PartyStatus.ACTIVE);
    }

    @DisplayName("confirm - 방장이 아닌 멤버면 NOT_PARTY_HOST")
    @Test
    void confirm_notHost() {

        /*
         * settings
         */
        Party party = open(3, MEMBER_ID);

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.confirm(MEMBER_ID, MEETING_AT, NOW));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.NOT_PARTY_HOST, party, PartyStatus.ACTIVE);
    }

    @DisplayName("confirm - 방에 없는 사람이면 NOT_PARTY_MEMBER")
    @Test
    void confirm_notMember() {

        /*
         * settings
         */
        Party party = open(3, MEMBER_ID);

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.confirm(OUTSIDER_ID, MEETING_AT, NOW));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.NOT_PARTY_MEMBER, party, PartyStatus.ACTIVE);
    }

    @DisplayName("confirm - 예약 방이면 NOT_REALTIME_PARTY 이고 출발 시각은 그대로다")
    @Test
    void confirm_reservation() {

        /*
         * settings
         */
        Party party = reserved();
        party.join(MEMBER_ID);
        Instant departureAt = party.getDepartureAt();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, MEETING_AT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.NOT_REALTIME_PARTY);
        assertThat(party.getDepartureAt()).isEqualTo(departureAt);
    }

    @DisplayName("confirm - 이미 확정한 방이면 ALREADY_CONFIRMED 이고 처음 약속 시간이 남는다")
    @Test
    void confirm_alreadyConfirmed() {

        /*
         * settings
         */
        Party party = open(3, MEMBER_ID);
        party.confirm(HOST_ID, MEETING_AT, NOW);
        long version = party.getVersion();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, MEETING_AT.plusSeconds(60), NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ALREADY_CONFIRMED);
        assertThat(party.getDepartureAt()).isEqualTo(MEETING_AT);
        assertThat(party.getVersion()).isEqualTo(version);
    }

    @DisplayName("confirm - 확정 뒤 이미 끝난 방에 다시 누르면 상태보다 확정 여부를 먼저 보고 ALREADY_CONFIRMED")
    @Test
    void confirm_alreadyConfirmedAfterFinished() {

        /*
         * settings
         */
        Party party = restored(PartyStatus.FINISHED, MEETING_AT, HOST_ID, MEMBER_ID);

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, MEETING_AT, NOW));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.ALREADY_CONFIRMED);
    }

    @DisplayName("confirm - 방장 혼자면 NOT_ENOUGH_MEMBERS")
    @Test
    void confirm_alone() {

        /*
         * settings
         */
        Party party = open(3);

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, MEETING_AT, NOW));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.NOT_ENOUGH_MEMBERS, party, PartyStatus.ACTIVE);
    }

    @DisplayName("confirm - 모집 중(ACTIVE/COMPLETED)이 아닌 방이면 PARTY_NOT_RECRUITING")
    @ParameterizedTest(name = "status={0}")
    @EnumSource(value = PartyStatus.class, names = {"ACTIVE", "COMPLETED"}, mode = EnumSource.Mode.EXCLUDE)
    void confirm_notRecruiting(PartyStatus status) {

        /*
         * settings
         */
        Party party = restored(status, null, HOST_ID, MEMBER_ID);

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, MEETING_AT, NOW));

        /*
         * verify
         */
        assertRejected(thrown, MatchingErrorCode.PARTY_NOT_RECRUITING, party, status);
    }

    @DisplayName("confirm - 확정해 모집이 끝난 방에는 새로 참여할 수 없다")
    @Test
    void confirm_blocksJoin() {

        /*
         * settings
         */
        Party party = open(3, MEMBER_ID);
        party.confirm(HOST_ID, MEETING_AT, NOW);

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.join(THIRD_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_CLOSED);
        assertThat(party.hasMember(THIRD_ID)).isFalse();
    }

    // ───────────────────────── isConfirmed ─────────────────────────

    @DisplayName("isConfirmed - 실시간 방은 확정 전 false, 확정 후 true")
    @Test
    void isConfirmed_realtime() {

        /*
         * settings
         */
        Party party = open(3, MEMBER_ID);

        /*
         * test
         */
        boolean beforeConfirm = party.isConfirmed();
        party.confirm(HOST_ID, MEETING_AT, NOW);
        boolean afterConfirm = party.isConfirmed();

        /*
         * verify
         */
        assertThat(beforeConfirm).isFalse();
        assertThat(afterConfirm).isTrue();
    }

    @DisplayName("isConfirmed - 예약 방은 만들자마자 확정이다")
    @Test
    void isConfirmed_reservation() {

        /*
         * test
         */
        Party party = reserved();

        /*
         * verify
         */
        assertThat(party.isConfirmed()).isTrue();
    }

    @DisplayName("isConfirmed - 약속 시간이 저장된 실시간 방을 복원하면 확정이다")
    @Test
    void isConfirmed_restored() {

        /*
         * test
         */
        Party confirmed = restored(PartyStatus.COMPLETED, MEETING_AT, HOST_ID, MEMBER_ID);
        Party unconfirmed = restored(PartyStatus.COMPLETED, null, HOST_ID, MEMBER_ID);

        /*
         * verify
         */
        assertThat(confirmed.isConfirmed()).isTrue();
        assertThat(unconfirmed.isConfirmed()).isFalse();
    }

    // ───────────────────────── leave ─────────────────────────

    @DisplayName("leave - 확정한 실시간 방은 방장도 일반 멤버도 나갈 수 없다")
    @Test
    void leave_confirmedRealtime() {

        /*
         * settings
         */
        Party party = open(3, MEMBER_ID);
        party.confirm(HOST_ID, MEETING_AT, NOW);
        long version = party.getVersion();

        /*
         * test
         */
        Throwable hostThrown = catchThrowable(() -> party.leave(HOST_ID));
        Throwable memberThrown = catchThrowable(() -> party.leave(MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(hostThrown, MatchingErrorCode.CANNOT_LEAVE_CONFIRMED_PARTY);
        assertBusinessException(memberThrown, MatchingErrorCode.CANNOT_LEAVE_CONFIRMED_PARTY);
        assertThat(party.hasMember(HOST_ID)).isTrue();
        assertThat(party.hasMember(MEMBER_ID)).isTrue();
        assertThat(party.isHost(HOST_ID)).isTrue();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(party.getVersion()).isEqualTo(version);
    }

    @DisplayName("leave - 예약 방은 참여한 순간부터 방장도 일반 멤버도 나갈 수 없다")
    @Test
    void leave_reservation() {

        /*
         * settings
         */
        Party party = reserved();
        party.join(MEMBER_ID);

        /*
         * test
         */
        Throwable hostThrown = catchThrowable(() -> party.leave(HOST_ID));
        Throwable memberThrown = catchThrowable(() -> party.leave(MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(hostThrown, MatchingErrorCode.CANNOT_LEAVE_CONFIRMED_PARTY);
        assertBusinessException(memberThrown, MatchingErrorCode.CANNOT_LEAVE_CONFIRMED_PARTY);
        assertThat(party.getMembers()).hasSize(2);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
    }

    @DisplayName("leave - 예약 방에 혼자 남은 방장도 나갈 수 없고, 대신 모집 만료 때 방이 취소된다")
    @Test
    void leave_loneReservationHost() {

        /*
         * settings
         */
        Party party = reserved();

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.leave(HOST_ID));
        party.expireRecruiting();

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.CANNOT_LEAVE_CONFIRMED_PARTY);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
    }

    @DisplayName("leave - 확정 전 실시간 방은 나갈 수 있다")
    @Test
    void leave_unconfirmedRealtime() {

        /*
         * settings
         */
        Party party = open(3, MEMBER_ID);

        /*
         * test
         */
        party.leave(MEMBER_ID);

        /*
         * verify
         */
        assertThat(party.hasMember(MEMBER_ID)).isFalse();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
    }

    @DisplayName("leave - 확정 전이면 정원이 차 COMPLETED 인 실시간 방도 나갈 수 있고 모집 중으로 돌아간다")
    @Test
    void leave_unconfirmedCompletedRealtime() {

        /*
         * settings
         */
        Party party = open(2, MEMBER_ID);

        /*
         * test
         */
        party.leave(HOST_ID);

        /*
         * verify
         */
        assertThat(party.hasMember(HOST_ID)).isFalse();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(party.getCompletedAt()).isNull();
        assertThat(party.isHost(MEMBER_ID)).isTrue();
    }

    @DisplayName("leave - 이미 끝난 확정 방은 확정 여부보다 모집 상태를 먼저 보고 PARTY_NOT_RECRUITING")
    @Test
    void leave_confirmedAfterFinished() {

        /*
         * settings
         */
        Party party = restored(PartyStatus.FINISHED, MEETING_AT, HOST_ID, MEMBER_ID);

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> party.leave(MEMBER_ID));

        /*
         * verify
         */
        assertBusinessException(thrown, MatchingErrorCode.PARTY_NOT_RECRUITING);
    }

    // ───────────────────────── 공통 도우미 ─────────────────────────

    /** HOST_ID 가 만든 실시간 방에 others 가 차례로 참여한다 */
    private static Party open(int capacity, Long... others) {
        Party party = Party.open(HOST_ID, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(capacity), Instant.now(),
                Radius.DEFAULT, Radius.DEFAULT, 12000, 25, "encoded-path");
        for (Long other : others) {
            party.join(other);
        }

        return party;
    }

    private static Party reserved() {
        Instant now = Instant.now();

        return Party.reserve(HOST_ID, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(3), now, now.plus(Duration.ofDays(1)));
    }

    /** 저장된 상태·약속 시간을 그대로 복원한 실시간 방. memberIds 앞쪽일수록 먼저 합류했고 첫 번째가 방장 */
    private static Party restored(PartyStatus status, Instant departureAt, Long... memberIds) {
        List<PartyMember> members = new ArrayList<>();
        for (int i = 0; i < memberIds.length; i++) {
            members.add(PartyMember.load(memberIds[i], NOW.minusSeconds(60 - i)));
        }

        return Party.load(100L, DEPARTURE, DESTINATION, Radius.DEFAULT, Radius.DEFAULT, "강남역", "판교역", new Capacity(3),
                members, memberIds[0], NOW.minus(Duration.ofMinutes(5)), status, 12000, 25, "encoded-path", null, 0L,
                RoomType.REALTIME, departureAt, null);
    }

    /** 거절된 확정은 약속 시간·상태·version 을 건드리지 않는다 */
    private static void assertRejected(Throwable thrown, ErrorCode expected, Party party, PartyStatus status) {
        assertBusinessException(thrown, expected);
        assertThat(party.getDepartureAt()).isNull();
        assertThat(party.getStatus()).isEqualTo(status);
        assertThat(party.isConfirmed()).isFalse();
    }

    private static void assertBusinessException(Throwable thrown, ErrorCode expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }
}
