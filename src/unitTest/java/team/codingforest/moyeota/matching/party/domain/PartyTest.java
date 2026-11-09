package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static team.codingforest.moyeota.matching.party.domain.PartyFixtures.*;

/**
 *  방 만들기·참여·나가기·방장 승계. 정원이 차도 ACTIVE 이고 참여만 PARTY_FULL 로 막힌다
 */
class PartyTest {

    // ───────────────────────── create ─────────────────────────

    @DisplayName("create - 만든 사람이 방장으로 들어간 ACTIVE 실시간 방이다")
    @Test
    void create() {
        Party party = realtime(3);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(party.getRoomType()).isEqualTo(RoomType.REALTIME);
        assertThat(party.isHost(HOST_ID)).isTrue();
        assertThat(party.memberIds()).containsExactly(HOST_ID);
        assertThat(party.isConfirmed()).isFalse();
        assertThat(party.getDepartureAt()).isNull();
        assertThat(party.getVersion()).isZero();
    }

    @DisplayName("create - 출발지와 도착지가 같으면 SAME_DEPARTURE_DESTINATION")
    @Test
    void create_samePlace() {
        Throwable thrown = catchThrowable(() -> Party.create(HOST_ID, DEPARTURE, DEPARTURE, DEPARTURE_PLACE, DEPARTURE_PLACE, new Capacity(3), NOW, FARE, MINUTES, ROUTE));

        assertRejected(thrown, MatchingErrorCode.SAME_DEPARTURE_DESTINATION);
    }

    @DisplayName("create - 경로·요금·시간이 비었거나 음수면 INVALID_ROUTE_ESTIMATE")
    @Test
    void create_invalidEstimate() {
        assertRejected(catchThrowable(() -> Party.create(HOST_ID, DEPARTURE, DESTINATION, DEPARTURE_PLACE, DESTINATION_PLACE, new Capacity(3), NOW, null, MINUTES, ROUTE)), MatchingErrorCode.INVALID_ROUTE_ESTIMATE);
        assertRejected(catchThrowable(() -> Party.create(HOST_ID, DEPARTURE, DESTINATION, DEPARTURE_PLACE, DESTINATION_PLACE, new Capacity(3), NOW, FARE, -1, ROUTE)), MatchingErrorCode.INVALID_ROUTE_ESTIMATE);
        assertRejected(catchThrowable(() -> Party.create(HOST_ID, DEPARTURE, DESTINATION, DEPARTURE_PLACE, DESTINATION_PLACE, new Capacity(3), NOW, FARE, MINUTES, null)), MatchingErrorCode.INVALID_ROUTE_ESTIMATE);
    }

    @DisplayName("create - 정원 1인 방도 만들자마자 ACTIVE 이고 정원이 찼다")
    @Test
    void create_soloIsFullButActive() {
        Party party = realtime(1);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(party.isFull()).isTrue();
    }

    // ───────────────────────── reserve ─────────────────────────

    @DisplayName("reserve - 출발 시각이 있는 ACTIVE 예약 방이고 만들자마자 약속이 정해진 방이다. 경로·요금·시간은 없다")
    @Test
    void reserve() {
        Party party = reserved(3);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(party.getRoomType()).isEqualTo(RoomType.RESERVED);
        assertThat(party.isConfirmed()).isTrue();
        assertThat(party.getDepartureAt()).isEqualTo(RESERVED_AT);
        assertThat(party.getEstimatedFare()).isNull();
        assertThat(party.getEstimatedTime()).isNull();
        assertThat(party.getRoute()).isNull();
    }

    @DisplayName("reserve - 출발 시각이 지금이거나 과거, 7일 넘게 뒤면 INVALID_DEPARTURE_TIME")
    @Test
    void reserve_invalidDepartureAt() {
        assertRejected(catchThrowable(() -> Party.reserve(HOST_ID, DEPARTURE, DESTINATION, DEPARTURE_PLACE, DESTINATION_PLACE, new Capacity(3), NOW, NOW)), MatchingErrorCode.INVALID_DEPARTURE_TIME);
        assertRejected(catchThrowable(() -> Party.reserve(HOST_ID, DEPARTURE, DESTINATION, DEPARTURE_PLACE, DESTINATION_PLACE, new Capacity(3), NOW, NOW.plus(Duration.ofDays(7)).plusSeconds(1))), MatchingErrorCode.INVALID_DEPARTURE_TIME);
    }

    @DisplayName("reserve - 정확히 7일 뒤는 받는다")
    @Test
    void reserve_sevenDaysAhead() {
        Party party = Party.reserve(HOST_ID, DEPARTURE, DESTINATION, DEPARTURE_PLACE, DESTINATION_PLACE, new Capacity(3), NOW, NOW.plus(Duration.ofDays(7)));

        assertThat(party.getDepartureAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    }

    // ───────────────────────── join ─────────────────────────

    @DisplayName("join - 들어온 멤버가 합류 순으로 쌓이고 version 이 1 오른다")
    @Test
    void join() {
        Party party = realtime(3);

        party.join(MEMBER_ID, NOW.plusSeconds(1));

        assertThat(party.memberIds()).containsExactly(HOST_ID, MEMBER_ID);
        assertThat(party.getMembers().get(1).getJoinedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(party.getVersion()).isEqualTo(1);
        assertThat(party.isHost(MEMBER_ID)).isFalse();
    }

    @DisplayName("join - 정원이 차도 상태는 ACTIVE 그대로이고 다음 참여는 PARTY_FULL")
    @Test
    void join_full() {
        Party party = realtime(2, MEMBER_ID);

        Throwable thrown = catchThrowable(() -> party.join(THIRD_ID, NOW));

        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(party.isFull()).isTrue();
        assertRejected(thrown, MatchingErrorCode.PARTY_FULL);
        assertThat(party.memberIds()).containsExactly(HOST_ID, MEMBER_ID);
    }

    @DisplayName("join - 약속을 확정한 방(COMPLETED)과 닫힌 방은 PARTY_CLOSED - 정원이 남아 있어도 들어올 수 없다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"COMPLETED", "FINISHED", "CANCELED"})
    void join_notActive(PartyStatus status) {
        Party party = restored(RoomType.REALTIME, status, MEETING_AT, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));

        Throwable thrown = catchThrowable(() -> party.join(THIRD_ID, NOW));

        assertRejected(thrown, MatchingErrorCode.PARTY_CLOSED);
    }

    @DisplayName("join - 출발 시각이 된 예약 방은 모집 정리 전이어도 PARTY_CLOSED")
    @Test
    void join_reservationAtDeparture() {
        Party party = reserved(3);

        Throwable thrown = catchThrowable(() -> party.join(MEMBER_ID, RESERVED_AT));

        assertRejected(thrown, MatchingErrorCode.PARTY_CLOSED);
    }

    @DisplayName("join - 이미 들어온 멤버면 ALREADY_JOINED_PARTY")
    @Test
    void join_twice() {
        Party party = realtime(3, MEMBER_ID);

        Throwable thrown = catchThrowable(() -> party.join(MEMBER_ID, NOW));

        assertRejected(thrown, MatchingErrorCode.ALREADY_JOINED_PARTY);
    }

    // ───────────────────────── leave ─────────────────────────

    @DisplayName("leave - 방장이 아닌 멤버가 나가면 방장은 그대로고 version 이 1 오른다")
    @Test
    void leave_member() {
        Party party = realtime(3, MEMBER_ID, THIRD_ID);
        long before = party.getVersion();

        party.leave(MEMBER_ID);

        assertThat(party.memberIds()).containsExactly(HOST_ID, THIRD_ID);
        assertThat(party.isHost(HOST_ID)).isTrue();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(party.getVersion()).isEqualTo(before + 1);
    }

    @DisplayName("leave - 방장이 나가면 남은 멤버 중 가장 먼저 합류한 사람이 승계한다")
    @Test
    void leave_hostSucceeded() {
        Party party = realtime(3, MEMBER_ID, THIRD_ID);

        party.leave(HOST_ID);

        assertThat(party.getHostId()).isEqualTo(MEMBER_ID);
    }

    @DisplayName("leave - 합류 시각이 같으면 memberId 가 작은 멤버가 승계한다")
    @Test
    void leave_hostSucceededByLowerId() {
        Party party = restored(RoomType.REALTIME, PartyStatus.ACTIVE, null, waiting(HOST_ID, 0), waiting(7L, 1), waiting(5L, 1));

        party.leave(HOST_ID);

        assertThat(party.getHostId()).isEqualTo(5L);
    }

    @DisplayName("leave - 마지막 멤버가 나가면 CANCELED 이고 방장이 없다")
    @Test
    void leave_last() {
        Party party = realtime(3);

        party.leave(HOST_ID);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(party.getHostId()).isNull();
        assertThat(party.memberIds()).isEmpty();
    }

    @DisplayName("leave - 정원이 찼던 방에서 나가면 다시 들어올 수 있다")
    @Test
    void leave_reopensSeat() {
        Party party = realtime(2, MEMBER_ID);

        party.leave(MEMBER_ID);
        party.join(THIRD_ID, NOW);

        assertThat(party.memberIds()).containsExactly(HOST_ID, THIRD_ID);
    }

    @DisplayName("leave - 약속을 확정한 실시간 방에서도 나갈 수 있다")
    @Test
    void leave_confirmed() {
        Party party = confirmed(3, MEMBER_ID, THIRD_ID);

        party.leave(MEMBER_ID);

        assertThat(party.memberIds()).containsExactly(HOST_ID, THIRD_ID);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
        assertThat(party.getDepartureAt()).isEqualTo(MEETING_AT);
    }

    @DisplayName("leave - 확정한 실시간 방에서 나가 1명만 남으면 CANCELED 다 - 약속이 잡힌 뒤라 더 모을 수 없고 혼자서는 출발할 수 없다")
    @Test
    void leave_confirmedLeavesOne() {
        Party party = confirmed(3, MEMBER_ID);

        party.leave(MEMBER_ID);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(party.memberIds()).containsExactly(HOST_ID);
        assertThat(party.getHostId()).isEqualTo(HOST_ID);
    }

    @DisplayName("leave - 예약 방은 참여한 뒤에도 나갈 수 있고, 혼자 남아도 출발 전이라 ACTIVE 로 다시 모은다")
    @Test
    void leave_reservationLeavesOne() {
        Party party = reserved(3, MEMBER_ID);

        party.leave(MEMBER_ID);

        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(party.memberIds()).containsExactly(HOST_ID);
    }

    @DisplayName("leave - 출발지에 도착한 멤버는 나갈 수 없다 - 같이 탄 뒤 나가서 정산을 피하는 길을 막는다")
    @Test
    void leave_arrived() {
        Party party = confirmed(3, MEMBER_ID, THIRD_ID);
        party.arrive(MEMBER_ID, true, MEETING_AT);

        Throwable thrown = catchThrowable(() -> party.leave(MEMBER_ID));

        assertRejected(thrown, MatchingErrorCode.ARRIVED_MEMBER_CANNOT_LEAVE);
        assertThat(party.memberIds()).containsExactly(HOST_ID, MEMBER_ID, THIRD_ID);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
    }

    @DisplayName("leave - 방에 없는 사람이면 NOT_PARTY_MEMBER")
    @Test
    void leave_notMember() {
        Party party = realtime(3, MEMBER_ID);

        Throwable thrown = catchThrowable(() -> party.leave(OUTSIDER_ID));

        assertRejected(thrown, MatchingErrorCode.NOT_PARTY_MEMBER);
    }

    @DisplayName("leave - 닫힌 방이면 PARTY_CLOSED")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED", "CANCELED"})
    void leave_closed(PartyStatus status) {
        Party party = restored(RoomType.REALTIME, status, MEETING_AT, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));

        Throwable thrown = catchThrowable(() -> party.leave(MEMBER_ID));

        assertRejected(thrown, MatchingErrorCode.PARTY_CLOSED);
    }

    // ───────────────────────── load·조회 ─────────────────────────

    @DisplayName("load - 저장된 방장을 그대로 복원하고, 멤버가 없는 방은 방장이 없다")
    @Test
    void load_host() {
        Party party = restored(RoomType.REALTIME, PartyStatus.ACTIVE, null, waiting(MEMBER_ID, 1), waiting(HOST_ID, 0));
        Party empty = restored(RoomType.REALTIME, PartyStatus.CANCELED, null);

        assertThat(party.getHostId()).isEqualTo(MEMBER_ID);
        assertThat(empty.getHostId()).isNull();
    }

    @DisplayName("isHost - 방장이 아닌 멤버, 방에 없는 사람, null 은 방장이 아니다")
    @Test
    void isHost() {
        Party party = realtime(3, MEMBER_ID);

        assertThat(party.isHost(MEMBER_ID)).isFalse();
        assertThat(party.isHost(OUTSIDER_ID)).isFalse();
        assertThat(party.isHost(null)).isFalse();
    }

    @DisplayName("getMembers - 밖에서 멤버 목록을 바꿀 수 없다")
    @Test
    void getMembers_unmodifiable() {
        Party party = realtime(3);

        Throwable thrown = catchThrowable(() -> party.getMembers().clear());

        assertThat(thrown).isInstanceOf(UnsupportedOperationException.class);
    }
}
