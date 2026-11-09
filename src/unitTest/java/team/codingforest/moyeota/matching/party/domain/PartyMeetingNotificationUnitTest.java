package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static team.codingforest.moyeota.matching.party.domain.PartyFixtures.*;

/**
 *  알림 받는 사람 - 언제 보낼지는 스케줄러가 departure_at 구간으로 고르고, 누구에게 보낼지는 Party 가 정한다
 */
class PartyMeetingNotificationUnitTest {

    // ───────────────────────── noticeRecipients ─────────────────────────

    @DisplayName("noticeRecipients - 열린 확정 방이면 도착 여부와 상관없이 멤버 전원(합류 순)이다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"ACTIVE", "COMPLETED"})
    void noticeRecipients(PartyStatus status) {

        /*
         * settings
         */
        Party party = restored(RoomType.REALTIME, status, MEETING_AT, waiting(HOST_ID, 0), arrived(MEMBER_ID, 1), waiting(THIRD_ID, 2));

        /*
         * verify
         */
        assertThat(party.noticeRecipients()).containsExactly(HOST_ID, MEMBER_ID, THIRD_ID);
    }

    @DisplayName("noticeRecipients - 예약 방은 만들 때부터 약속이 있어 ACTIVE 인 채로 멤버 전원이다")
    @Test
    void noticeRecipients_reservation() {

        /*
         * settings
         */
        Party party = reserved(3, MEMBER_ID);

        /*
         * verify
         */
        assertThat(party.noticeRecipients()).containsExactly(HOST_ID, MEMBER_ID);
    }

    @DisplayName("noticeRecipients - 약속을 확정하지 않은 실시간 방은 아무도 받지 않는다")
    @Test
    void noticeRecipients_unconfirmed() {

        /*
         * settings
         */
        Party party = realtime(3, MEMBER_ID);

        /*
         * verify
         */
        assertThat(party.noticeRecipients()).isEmpty();
    }

    @DisplayName("noticeRecipients - 혼자인 방은 함께 탈 사람이 없어 아무도 받지 않는다")
    @Test
    void noticeRecipients_alone() {

        /*
         * settings
         */
        Party party = reserved(3);

        /*
         * verify
         */
        assertThat(party.noticeRecipients()).isEmpty();
    }

    @DisplayName("noticeRecipients - 닫힌 방은 아무도 받지 않는다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED", "CANCELED"})
    void noticeRecipients_closed(PartyStatus status) {

        /*
         * settings
         */
        Party party = restored(RoomType.REALTIME, status, MEETING_AT, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1));

        /*
         * verify
         */
        assertThat(party.noticeRecipients()).isEmpty();
    }

    // ───────────────────────── departureReminderRecipients ─────────────────────────

    @DisplayName("departureReminderRecipients - 출발 버튼을 누르는 방장 한 명이다. 방장이 아직 도착 전이어도 받는다")
    @Test
    void departureReminderRecipients() {

        /*
         * settings
         */
        Party party = restored(RoomType.REALTIME, PartyStatus.COMPLETED, MEETING_AT, waiting(HOST_ID, 0), arrived(MEMBER_ID, 1), arrived(THIRD_ID, 2));

        /*
         * verify
         */
        assertThat(party.departureReminderRecipients()).containsExactly(HOST_ID);
    }

    @DisplayName("departureReminderRecipients - 방장이 나가 승계됐으면 새 방장이 받는다")
    @Test
    void departureReminderRecipients_succeededHost() {

        /*
         * settings
         */
        Party party = confirmed(3, MEMBER_ID, THIRD_ID);
        party.leave(HOST_ID);

        /*
         * verify
         */
        assertThat(party.departureReminderRecipients()).containsExactly(MEMBER_ID);
    }

    @DisplayName("departureReminderRecipients - 미확정 방·혼자인 방·닫힌 방은 아무도 받지 않는다")
    @Test
    void departureReminderRecipients_notDue() {

        /*
         * settings
         */
        Party unconfirmed = realtime(3, MEMBER_ID);
        Party alone = reserved(3);
        Party finished = restored(RoomType.REALTIME, PartyStatus.FINISHED, MEETING_AT, arrived(HOST_ID, 0), arrived(MEMBER_ID, 1));

        /*
         * verify
         */
        assertThat(unconfirmed.departureReminderRecipients()).isEmpty();
        assertThat(alone.departureReminderRecipients()).isEmpty();
        assertThat(finished.departureReminderRecipients()).isEmpty();
    }

    @DisplayName("받는 사람을 묻는 것만으로는 방이 바뀌지 않는다 - 알림 기록이 없어 version 도 그대로다")
    @Test
    void recipients_readOnly() {

        /*
         * settings
         */
        Party party = confirmed(3, MEMBER_ID);
        long version = party.getVersion();

        /*
         * test
         */
        party.noticeRecipients();
        party.departureReminderRecipients();

        /*
         * verify
         */
        assertThat(party.getVersion()).isEqualTo(version);
        assertThat(party.getStatus()).isEqualTo(PartyStatus.COMPLETED);
    }

    // ───────────────────────── DEPARTURE_REMINDER_OFFSETS ─────────────────────────

    @DisplayName("DEPARTURE_REMINDER_OFFSETS - 출발 재촉은 약속 시각 뒤 30분, 1시간, 2시간, 4시간 네 번이다")
    @Test
    void departureReminderOffsets() {
        assertThat(Party.DEPARTURE_REMINDER_OFFSETS).containsExactly(
                Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(4));
    }
}
