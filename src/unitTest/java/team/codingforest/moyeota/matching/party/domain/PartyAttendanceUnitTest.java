package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static team.codingforest.moyeota.matching.party.domain.PartyFixtures.*;

/**
 *  출발지 도착과 출발. 도착한 멤버가 탄 사람(정산 대상)이고, 출발은 도착한 방장만 누른다
 */
class PartyAttendanceUnitTest {
    private static final Instant ARRIVAL_OPENS_AT = MEETING_AT.minus(Party.ARRIVAL_OPENS_BEFORE);

    // ───────────────────────── arrive ─────────────────────────

    @DisplayName("arrive - 약속 10분 전부터 출발지 100m 안의 멤버는 도착 시각이 남고 version 이 1 오른다")
    @Test
    void arrive() {
        Party party = confirmed(3, MEMBER_ID);
        long before = party.getVersion();

        boolean first = party.arrive(MEMBER_ID, true, ARRIVAL_OPENS_AT);

        assertThat(first).isTrue();
        assertThat(party.getMembers().get(1).getArrivedAt()).isEqualTo(ARRIVAL_OPENS_AT);
        assertThat(party.riderIds()).containsExactly(MEMBER_ID);
        assertThat(party.getVersion()).isEqualTo(before + 1);
    }

    @DisplayName("arrive - 이미 도착한 멤버가 다시 누르면 false 이고 처음 시각과 version 이 그대로다")
    @Test
    void arrive_again() {
        Party party = confirmed(3, MEMBER_ID);
        party.arrive(MEMBER_ID, true, MEETING_AT);
        long before = party.getVersion();

        boolean first = party.arrive(MEMBER_ID, false, MEETING_AT.plusSeconds(60));

        assertThat(first).isFalse();
        assertThat(party.getMembers().get(1).getArrivedAt()).isEqualTo(MEETING_AT);
        assertThat(party.getVersion()).isEqualTo(before);
    }

    @DisplayName("arrive - 약속 10분 1초 전이면 ARRIVAL_NOT_OPEN 이고, 출발지 밖이라는 답보다 먼저다")
    @Test
    void arrive_notOpen() {
        Party party = confirmed(3, MEMBER_ID);

        Throwable thrown = catchThrowable(() -> party.arrive(MEMBER_ID, false, ARRIVAL_OPENS_AT.minusSeconds(1)));

        assertRejected(thrown, MatchingErrorCode.ARRIVAL_NOT_OPEN);
        assertThat(party.riderIds()).isEmpty();
    }

    @DisplayName("arrive - 출발지 100m 밖이면 TOO_FAR_FROM_DEPARTURE 이고 도착이 남지 않는다")
    @Test
    void arrive_tooFar() {
        Party party = confirmed(3, MEMBER_ID);

        Throwable thrown = catchThrowable(() -> party.arrive(MEMBER_ID, false, MEETING_AT));

        assertRejected(thrown, MatchingErrorCode.TOO_FAR_FROM_DEPARTURE);
        assertThat(party.riderIds()).isEmpty();
    }

    @DisplayName("arrive - 방에 없는 사람이면 NOT_PARTY_MEMBER")
    @Test
    void arrive_notMember() {
        Party party = confirmed(3, MEMBER_ID);

        Throwable thrown = catchThrowable(() -> party.arrive(OUTSIDER_ID, true, MEETING_AT));

        assertRejected(thrown, MatchingErrorCode.NOT_PARTY_MEMBER);
    }

    @DisplayName("arrive - 약속을 확정하지 않은 실시간 방이면 PARTY_NOT_CONFIRMED")
    @Test
    void arrive_unconfirmed() {
        Party party = realtime(3, MEMBER_ID);

        Throwable thrown = catchThrowable(() -> party.arrive(MEMBER_ID, true, NOW));

        assertRejected(thrown, MatchingErrorCode.PARTY_NOT_CONFIRMED);
    }

    @DisplayName("arrive - 닫힌 방이면 이미 도착한 멤버라도 PARTY_CLOSED")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED", "CANCELED"})
    void arrive_closed(PartyStatus status) {
        Party party = restored(RoomType.REALTIME, status, MEETING_AT, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1));

        assertRejected(catchThrowable(() -> party.arrive(HOST_ID, true, MEETING_AT)), MatchingErrorCode.PARTY_CLOSED);
        assertRejected(catchThrowable(() -> party.arrive(MEMBER_ID, true, MEETING_AT)), MatchingErrorCode.PARTY_CLOSED);
    }

    @DisplayName("arrive - 예약 방은 출발 시각 10분 전부터 ACTIVE 인 채로 도착을 받는다")
    @Test
    void arrive_reservation() {
        Party party = reserved(3, MEMBER_ID);

        party.arrive(MEMBER_ID, true, RESERVED_AT.minus(Party.ARRIVAL_OPENS_BEFORE));

        assertThat(party.riderIds()).containsExactly(MEMBER_ID);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
    }

    // ───────────────────────── depart ─────────────────────────

    @DisplayName("depart - 도착한 방장이 약속 시간에 누르면 FINISHED 이고 도착한 멤버만 탄 사람으로 남는다")
    @Test
    void depart() {
        Party party = confirmed(3, MEMBER_ID, THIRD_ID);
        party.arrive(HOST_ID, true, MEETING_AT);
        party.arrive(THIRD_ID, true, MEETING_AT);
        long before = party.getVersion();

        party.depart(HOST_ID, MEETING_AT);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.riderIds()).containsExactly(HOST_ID, THIRD_ID);
        assertThat(party.memberIds()).containsExactly(HOST_ID, MEMBER_ID, THIRD_ID);
        assertThat(party.getVersion()).isEqualTo(before + 1);
    }

    @DisplayName("depart - 방장이 아닌 멤버는 도착했어도 NOT_PARTY_HOST, 방에 없는 사람은 NOT_PARTY_MEMBER")
    @Test
    void depart_notHost() {
        Party party = confirmed(3, MEMBER_ID);
        party.arrive(MEMBER_ID, true, MEETING_AT);

        assertRejected(catchThrowable(() -> party.depart(MEMBER_ID, MEETING_AT)), MatchingErrorCode.NOT_PARTY_HOST);
        assertRejected(catchThrowable(() -> party.depart(OUTSIDER_ID, MEETING_AT)), MatchingErrorCode.NOT_PARTY_MEMBER);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
    }

    @DisplayName("depart - 방장이 아직 도착 전이면 NOT_ARRIVED")
    @Test
    void depart_hostNotArrived() {
        Party party = confirmed(3, MEMBER_ID);
        party.arrive(MEMBER_ID, true, MEETING_AT);

        Throwable thrown = catchThrowable(() -> party.depart(HOST_ID, MEETING_AT));

        assertRejected(thrown, MatchingErrorCode.NOT_ARRIVED);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
    }

    @DisplayName("depart - 약속 시간 1초 전이면 MEETING_NOT_STARTED")
    @Test
    void depart_beforeMeeting() {
        Party party = confirmed(3, MEMBER_ID);
        party.arrive(HOST_ID, true, ARRIVAL_OPENS_AT);

        Throwable thrown = catchThrowable(() -> party.depart(HOST_ID, MEETING_AT.minusSeconds(1)));

        assertRejected(thrown, MatchingErrorCode.MEETING_NOT_STARTED);
    }

    @DisplayName("depart - 약속을 확정하지 않은 실시간 방이면 PARTY_NOT_CONFIRMED")
    @Test
    void depart_unconfirmed() {
        Party party = realtime(3, MEMBER_ID);

        Throwable thrown = catchThrowable(() -> party.depart(HOST_ID, NOW));

        assertRejected(thrown, MatchingErrorCode.PARTY_NOT_CONFIRMED);
    }

    @DisplayName("depart - 닫힌 방이면 PARTY_CLOSED - 이미 출발로 끝난 방 포함")
    @Test
    void depart_closed() {
        Party party = confirmed(3, MEMBER_ID);
        party.arrive(HOST_ID, true, MEETING_AT);
        party.depart(HOST_ID, MEETING_AT);

        Throwable thrown = catchThrowable(() -> party.depart(HOST_ID, MEETING_AT.plusSeconds(1)));

        assertRejected(thrown, MatchingErrorCode.PARTY_CLOSED);
    }

    @DisplayName("depart - 아무도 들어오지 않은 예약 방(방장 혼자)은 NOT_ENOUGH_MEMBERS - 모집 만료가 닫을 방이다")
    @Test
    void depart_alone() {
        Party party = reserved(3);
        party.arrive(HOST_ID, true, RESERVED_AT);

        Throwable thrown = catchThrowable(() -> party.depart(HOST_ID, RESERVED_AT));

        assertRejected(thrown, MatchingErrorCode.NOT_ENOUGH_MEMBERS);
    }

    @DisplayName("depart - 출발 시각이 지났는데 아직 ACTIVE 인 예약 방도 바로 FINISHED 로 끝난다")
    @Test
    void depart_reservation() {
        Party party = reserved(3, MEMBER_ID);
        party.arrive(HOST_ID, true, RESERVED_AT);

        party.depart(HOST_ID, RESERVED_AT.plus(Duration.ofMinutes(3)));

        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.riderIds()).containsExactly(HOST_ID);
    }

    @DisplayName("riderIds - 도착하지 않은 멤버는 방이 끝나도 탄 사람이 아니다")
    @Test
    void riderIds_excludesAbsent() {
        Party party = restored(RoomType.REALTIME, PartyStatus.FINISHED, MEETING_AT, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1), arrived(THIRD_ID, 2));

        assertThat(party.riderIds()).containsExactly(HOST_ID, THIRD_ID);
    }
}
