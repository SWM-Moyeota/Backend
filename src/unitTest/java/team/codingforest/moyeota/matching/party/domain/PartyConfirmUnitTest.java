package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.codingforest.moyeota.common.exception.ErrorCode;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.enums.RoomType;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static team.codingforest.moyeota.matching.party.domain.PartyFixtures.*;

/**
 *  약속 확정 - 실시간 방의 방장이 약속 시간(지금부터 5분 이상 1시간 이하)과 만남 장소를 정하면 COMPLETED 가 된다.
 *  만남 장소는 늘 받는다 - 바꾸지 않으면 앱이 지금 출발지를 그대로 보내 그 장소로 고정된다
 */
class PartyConfirmUnitTest {

    @DisplayName("confirm - 약속 시간이 담기고 COMPLETED 가 되며 version 이 1 오른다. 지금 출발지를 그대로 받으면 출발지는 그대로다")
    @Test
    void confirm() {
        Party party = realtime(3, MEMBER_ID);
        long before = party.getVersion();

        party.confirm(HOST_ID, planAt(MEETING_AT), NOW);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(party.getDepartureAt()).isEqualTo(MEETING_AT);
        assertThat(party.isConfirmed()).isTrue();
        assertThat(party.getDepartureLocation()).isEqualTo(DEPARTURE);
        assertThat(party.getDeparture()).isEqualTo(DEPARTURE_PLACE);
        assertThat(party.getVersion()).isEqualTo(before + 1);
    }

    @DisplayName("confirm - 다른 만남 장소를 받으면 출발지 좌표와 이름이 바뀐다. 목적지·경로·요금은 그대로다")
    @Test
    void confirm_withMeetingPlace() {
        Party party = realtime(3, MEMBER_ID);

        party.confirm(HOST_ID, new MeetingPlan(MEETING_AT, OTHER_EXIT, "강남역 2번 출구"), NOW);

        assertThat(party.getDepartureLocation()).isEqualTo(OTHER_EXIT);
        assertThat(party.getDeparture()).isEqualTo("강남역 2번 출구");
        assertThat(party.getDestinationLocation()).isEqualTo(DESTINATION);
        assertThat(party.getRoute()).isEqualTo(ROUTE);
        assertThat(party.getEstimatedFare()).isEqualTo(FARE);
    }

    @DisplayName("confirm - 만남 장소가 도착지와 같으면 SAME_DEPARTURE_DESTINATION 이고 방은 그대로다")
    @Test
    void confirm_meetingPlaceEqualsDestination() {
        Party party = realtime(3, MEMBER_ID);

        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, new MeetingPlan(MEETING_AT, DESTINATION, DESTINATION_PLACE), NOW));

        assertUnchanged(thrown, MatchingErrorCode.SAME_DEPARTURE_DESTINATION, party);
    }

    @DisplayName("confirm - 정원이 찬 방도 확정할 수 있다")
    @Test
    void confirm_full() {
        Party party = realtime(2, MEMBER_ID);

        party.confirm(HOST_ID, planAt(MEETING_AT), NOW);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
    }

    @DisplayName("confirm - 약속 시간 경계값인 정확히 5분 뒤와 1시간 뒤는 받는다")
    @Test
    void confirm_boundaries() {
        Party inFive = realtime(3, MEMBER_ID);
        Party inAnHour = realtime(3, MEMBER_ID);

        inFive.confirm(HOST_ID, planAt(NOW.plus(Party.MIN_CONFIRM_AHEAD)), NOW);
        inAnHour.confirm(HOST_ID, planAt(NOW.plus(Party.MAX_CONFIRM_AHEAD)), NOW);

        assertThat(inFive.getDepartureAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(inAnHour.getDepartureAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @DisplayName("confirm - 5분 뒤보다 1초라도 이르면 INVALID_MEETING_TIME - 지금을 약속으로 잡아 바로 출발하지 못하게 막는다")
    @Test
    void confirm_tooSoon() {
        Party party = realtime(3, MEMBER_ID);

        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, planAt(NOW.plus(Duration.ofMinutes(5)).minusSeconds(1)), NOW));

        assertUnchanged(thrown, MatchingErrorCode.INVALID_MEETING_TIME, party);
    }

    @DisplayName("confirm - 1시간보다 1초라도 늦으면 INVALID_MEETING_TIME")
    @Test
    void confirm_tooLate() {
        Party party = realtime(3, MEMBER_ID);

        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, planAt(NOW.plus(Duration.ofHours(1)).plusSeconds(1)), NOW));

        assertUnchanged(thrown, MatchingErrorCode.INVALID_MEETING_TIME, party);
    }

    @DisplayName("confirm - 약속 시간이 null 이면 INVALID_MEETING_TIME")
    @Test
    void confirm_nullTime() {
        Party party = realtime(3, MEMBER_ID);

        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, planAt(null), NOW));

        assertUnchanged(thrown, MatchingErrorCode.INVALID_MEETING_TIME, party);
    }

    @DisplayName("confirm - 방에 없는 사람은 NOT_PARTY_MEMBER, 방장이 아닌 멤버는 NOT_PARTY_HOST")
    @Test
    void confirm_notHost() {
        Party party = realtime(3, MEMBER_ID);

        assertUnchanged(catchThrowable(() -> party.confirm(OUTSIDER_ID, planAt(MEETING_AT), NOW)), MatchingErrorCode.NOT_PARTY_MEMBER, party);
        assertUnchanged(catchThrowable(() -> party.confirm(MEMBER_ID, planAt(MEETING_AT), NOW)), MatchingErrorCode.NOT_PARTY_HOST, party);
    }

    @DisplayName("confirm - 예약 방은 NOT_REALTIME_PARTY 이고 출발 시각은 그대로다")
    @Test
    void confirm_reservation() {
        Party party = reserved(3, MEMBER_ID);

        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, planAt(MEETING_AT), NOW));

        assertRejected(thrown, MatchingErrorCode.NOT_REALTIME_PARTY);
        assertThat(party.getDepartureAt()).isEqualTo(RESERVED_AT);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
    }

    @DisplayName("confirm - 이미 확정한 방은 ALREADY_CONFIRMED 이고 처음 약속이 남는다")
    @Test
    void confirm_twice() {
        Party party = confirmed(3, MEMBER_ID);

        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, planAt(MEETING_AT.plusSeconds(60)), NOW));

        assertRejected(thrown, MatchingErrorCode.ALREADY_CONFIRMED);
        assertThat(party.getDepartureAt()).isEqualTo(MEETING_AT);
    }

    @DisplayName("confirm - 닫힌 방은 PARTY_CLOSED")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED", "CANCELED"})
    void confirm_closed(PartyStatus status) {
        Party party = restored(RoomType.REALTIME, status, null, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));

        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, planAt(MEETING_AT), NOW));

        assertRejected(thrown, MatchingErrorCode.PARTY_CLOSED);
    }

    @DisplayName("confirm - 방장 혼자면 NOT_ENOUGH_MEMBERS")
    @Test
    void confirm_alone() {
        Party party = realtime(3);

        Throwable thrown = catchThrowable(() -> party.confirm(HOST_ID, planAt(MEETING_AT), NOW));

        assertUnchanged(thrown, MatchingErrorCode.NOT_ENOUGH_MEMBERS, party);
    }

    @DisplayName("isConfirmed - 약속 시간이 저장된 실시간 방을 복원하면 확정이다")
    @Test
    void isConfirmed_restored() {
        Party party = restored(RoomType.REALTIME, PartyStatus.COMPLETED, MEETING_AT, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));

        assertThat(party.isConfirmed()).isTrue();
    }

    /** 거절된 확정은 약속 시간·상태·출발지·version 을 건드리지 않는다 */
    private static void assertUnchanged(Throwable thrown, ErrorCode expected, Party party) {
        assertRejected(thrown, expected);
        assertThat(party.getDepartureAt()).isNull();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(party.getDepartureLocation()).isEqualTo(DEPARTURE);
        assertThat(party.isConfirmed()).isFalse();
    }
}
