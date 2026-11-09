package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static team.codingforest.moyeota.matching.party.domain.PartyFixtures.*;

/**
 *  스케줄러가 닫는 두 경로 - 모집 만료(혼자인 방)와 자동 정리(아무도 출발을 누르지 않은 방).
 *  언제 닫을지는 조회 쿼리가 고르고 여기서는 어떻게 닫을지만 정한다. 건너뛰기는 예외가 아니라 결과로 돌려준다
 */
class PartyAutoCloseUnitTest {

    // ───────────────────────── expireRecruiting ─────────────────────────

    @DisplayName("expireRecruiting - 혼자인 ACTIVE 방은 CANCELED 로 닫고 true 를 돌려준다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(RoomType.class)
    void expireRecruiting_alone(RoomType roomType) {
        Party party = roomType == RoomType.REALTIME ? realtime(3) : reserved(3);
        long before = party.getVersion();

        boolean canceled = party.expireRecruiting();

        assertThat(canceled).isTrue();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.CANCELED);
        assertThat(party.getVersion()).isEqualTo(before + 1);
    }

    @DisplayName("expireRecruiting - 2명 이상이면 아무것도 바꾸지 않고 false 다 - 15분은 혼자면 접는 기준이지 모집 마감이 아니다")
    @Test
    void expireRecruiting_withCompanion() {
        Party party = realtime(3, MEMBER_ID);
        long before = party.getVersion();

        boolean canceled = party.expireRecruiting();

        assertThat(canceled).isFalse();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.ACTIVE);
        assertThat(party.getVersion()).isEqualTo(before);
    }

    @DisplayName("expireRecruiting - ACTIVE 가 아닌 방은 아무것도 바꾸지 않고 false 다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"COMPLETED", "FINISHED", "CANCELED"})
    void expireRecruiting_notActive(PartyStatus status) {
        Party party = restored(RoomType.REALTIME, status, MEETING_AT, waiting(HOST_ID, 0));

        boolean canceled = party.expireRecruiting();

        assertThat(canceled).isFalse();
        assertThat(party.getStatus()).isEqualTo(status);
    }

    // ───────────────────────── autoClose ─────────────────────────

    @DisplayName("autoClose - 도착한 멤버가 있는 확정 방은 FINISHED 이고 도착한 멤버가 탄 사람이다")
    @Test
    void autoClose_finished() {
        Party party = restored(RoomType.REALTIME, PartyStatus.COMPLETED, MEETING_AT, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1), arrived(THIRD_ID, 2));
        long before = party.getVersion();

        AutoCloseResult result = party.autoClose();

        assertThat(result.handled()).isTrue();
        assertThat(result.status()).isEqualTo(PartyStatus.FINISHED);
        assertThat(result.riderIds()).containsExactly(HOST_ID, THIRD_ID);
        assertThat(result.hasRiders()).isTrue();
        assertThat(party.getStatus()).isEqualTo(PartyStatus.FINISHED);
        assertThat(party.getVersion()).isEqualTo(before + 1);
    }

    @DisplayName("autoClose - 아무도 도착하지 않은 확정 방은 동승이 없었으니 CANCELED 이고 탄 사람이 없다")
    @Test
    void autoClose_nobodyArrived() {
        Party party = restored(RoomType.REALTIME, PartyStatus.COMPLETED, MEETING_AT, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));

        AutoCloseResult result = party.autoClose();

        assertThat(result.status()).isEqualTo(PartyStatus.CANCELED);
        assertThat(result.riderIds()).isEmpty();
        assertThat(result.hasRiders()).isFalse();
    }

    @DisplayName("autoClose - 출발 시각이 지나 아직 ACTIVE 인 예약 방도 같은 기준이다")
    @Test
    void autoClose_reservation() {
        Party party = restored(RoomType.RESERVED, PartyStatus.ACTIVE, RESERVED_AT, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1));

        AutoCloseResult result = party.autoClose();

        assertThat(result.status()).isEqualTo(PartyStatus.FINISHED);
        assertThat(result.riderIds()).containsExactly(HOST_ID);
    }

    @DisplayName("autoClose - 약속이 없는 실시간 방은 멤버 수와 상관없이 CANCELED 이고 탄 사람이 없다")
    @Test
    void autoClose_unconfirmed() {
        Party party = restored(RoomType.REALTIME, PartyStatus.ACTIVE, null, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1), waiting(THIRD_ID, 2));

        AutoCloseResult result = party.autoClose();

        assertThat(result.handled()).isTrue();
        assertThat(result.status()).isEqualTo(PartyStatus.CANCELED);
        assertThat(result.hasRiders()).isFalse();
    }

    @DisplayName("autoClose - 혼자인 방은 도착했어도 혼자서는 탄 게 아니라 CANCELED 다")
    @Test
    void autoClose_alone() {
        Party party = restored(RoomType.RESERVED, PartyStatus.ACTIVE, RESERVED_AT, arrived(HOST_ID, 0));

        AutoCloseResult result = party.autoClose();

        assertThat(result.status()).isEqualTo(PartyStatus.CANCELED);
        assertThat(result.hasRiders()).isFalse();
    }

    @DisplayName("autoClose - 조회와 잠금 사이에 닫힌 방은 handled=false 이고 아무것도 바꾸지 않는다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED", "CANCELED"})
    void autoClose_alreadyClosed(PartyStatus status) {
        Party party = restored(RoomType.REALTIME, status, MEETING_AT, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1));
        long before = party.getVersion();

        AutoCloseResult result = party.autoClose();

        assertThat(result.handled()).isFalse();
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.hasRiders()).isFalse();
        assertThat(party.getVersion()).isEqualTo(before);
    }
}
