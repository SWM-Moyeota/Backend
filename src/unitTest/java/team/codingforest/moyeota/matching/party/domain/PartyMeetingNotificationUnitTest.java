package team.codingforest.moyeota.matching.party.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static team.codingforest.moyeota.matching.party.domain.PartyFixtures.*;

/**
 *  약속 시각 알림(markMeetingNotified)과 출발 재촉 차례(advanceDepartureReminder)
 */
class PartyMeetingNotificationUnitTest {
    private static final long VERSION = 7L;

    // ───────────────────────── markMeetingNotified ─────────────────────────

    @DisplayName("markMeetingNotified - 약속 시각이 되면 처리 시각을 남기고 멤버 전원(합류 순)에게 보낸다. version 은 그대로다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"ACTIVE", "COMPLETED"})
    void markMeetingNotified(PartyStatus status) {

        /*
         * settings
         */
        Party party = confirmedParty(status, null, 0, waiting(HOST_ID, 0), arrived(MEMBER_ID, 1), waiting(THIRD_ID, 2));

        /*
         * test
         */
        PartyNotice notice = party.markMeetingNotified(MEETING_AT);

        /*
         * verify
         */
        assertThat(notice.handled()).isTrue();
        assertThat(notice.shouldSend()).isTrue();
        assertThat(notice.recipientIds()).containsExactly(HOST_ID, MEMBER_ID, THIRD_ID);
        assertThat(party.getMeetingNotifiedAt()).isEqualTo(MEETING_AT);
        assertThat(party.getVersion()).isEqualTo(VERSION);
    }

    @DisplayName("markMeetingNotified - 약속 시각 전이면 차례가 아니고 아무것도 바꾸지 않는다")
    @Test
    void markMeetingNotified_beforeMeeting() {

        /*
         * settings
         */
        Party party = confirmedParty(PartyStatus.COMPLETED, null, 0, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));

        /*
         * test
         */
        PartyNotice notice = party.markMeetingNotified(MEETING_AT.minusSeconds(1));

        /*
         * verify
         */
        assertThat(notice.handled()).isFalse();
        assertThat(notice.shouldSend()).isFalse();
        assertThat(party.getMeetingNotifiedAt()).isNull();
    }

    @DisplayName("markMeetingNotified - 약속 시각에서 정확히 30분까지는 보낸다")
    @Test
    void markMeetingNotified_exactly30MinutesLate() {

        /*
         * settings
         */
        Party party = confirmedParty(PartyStatus.COMPLETED, null, 0, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));
        Instant now = MEETING_AT.plus(Duration.ofMinutes(30));

        /*
         * test
         */
        PartyNotice notice = party.markMeetingNotified(now);

        /*
         * verify
         */
        assertThat(notice.shouldSend()).isTrue();
        assertThat(notice.recipientIds()).containsExactly(HOST_ID, MEMBER_ID);
        assertThat(party.getMeetingNotifiedAt()).isEqualTo(now);
    }

    @DisplayName("markMeetingNotified - 약속 시각에서 30분 넘게 지났으면(서버가 멈췄던 경우) 보내지 않고 처리만 남긴다")
    @Test
    void markMeetingNotified_stale() {

        /*
         * settings
         */
        Party party = confirmedParty(PartyStatus.COMPLETED, null, 0, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));
        Instant now = MEETING_AT.plus(Duration.ofMinutes(30)).plusSeconds(1);

        /*
         * test
         */
        PartyNotice notice = party.markMeetingNotified(now);

        /*
         * verify
         */
        assertThat(notice.handled()).isTrue();
        assertThat(notice.shouldSend()).isFalse();
        assertThat(notice.recipientIds()).isEmpty();
        assertThat(party.getMeetingNotifiedAt()).isEqualTo(now);
    }

    @DisplayName("markMeetingNotified - 이미 처리한 방은 차례가 아니고 처음 처리 시각을 그대로 둔다")
    @Test
    void markMeetingNotified_alreadyNotified() {

        /*
         * settings
         */
        Instant notifiedAt = MEETING_AT.plusSeconds(20);
        Party party = confirmedParty(PartyStatus.COMPLETED, notifiedAt, 0, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));

        /*
         * test
         */
        PartyNotice notice = party.markMeetingNotified(MEETING_AT.plus(Duration.ofMinutes(1)));

        /*
         * verify
         */
        assertThat(notice.handled()).isFalse();
        assertThat(party.getMeetingNotifiedAt()).isEqualTo(notifiedAt);
    }

    @DisplayName("markMeetingNotified - 약속을 확정하지 않은 실시간 방은 차례가 아니다")
    @Test
    void markMeetingNotified_unconfirmed() {

        /*
         * settings
         */
        Party party = party(RoomType.REALTIME, null, PartyStatus.COMPLETED, null, 0, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));

        /*
         * test
         */
        PartyNotice notice = party.markMeetingNotified(MEETING_AT);

        /*
         * verify
         */
        assertThat(notice.handled()).isFalse();
        assertThat(party.getMeetingNotifiedAt()).isNull();
    }

    @DisplayName("markMeetingNotified - 이미 끝났거나 취소된 방은 차례가 아니다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"ACTIVE", "COMPLETED"}, mode = EnumSource.Mode.EXCLUDE)
    void markMeetingNotified_notCreate(PartyStatus status) {

        /*
         * settings
         */
        Party party = confirmedParty(status, null, 0, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1));

        /*
         * test
         */
        PartyNotice notice = party.markMeetingNotified(MEETING_AT.plusSeconds(10));

        /*
         * verify
         */
        assertThat(notice.handled()).isFalse();
        assertThat(party.getMeetingNotifiedAt()).isNull();
    }

    @DisplayName("markMeetingNotified - 예약 방은 출발 시각이 약속 시각이다")
    @Test
    void markMeetingNotified_reservation() {

        /*
         * settings
         */
        Party party = party(RoomType.RESERVED, MEETING_AT, PartyStatus.COMPLETED, null, 0, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));

        /*
         * test
         */
        PartyNotice notice = party.markMeetingNotified(MEETING_AT.plusSeconds(30));

        /*
         * verify
         */
        assertThat(notice.shouldSend()).isTrue();
        assertThat(notice.recipientIds()).containsExactly(HOST_ID, MEMBER_ID);
    }

    @DisplayName("markMeetingNotified - 함께 탈 사람이 들어오지 않은 예약 방(멤버 1명)은 보내지 않고 처리만 남긴다")
    @Test
    void markMeetingNotified_aloneInReservation() {

        /*
         * settings
         */
        Party party = party(RoomType.RESERVED, MEETING_AT, PartyStatus.ACTIVE, null, 0, waiting(HOST_ID, 0));

        /*
         * test
         */
        PartyNotice notice = party.markMeetingNotified(MEETING_AT);

        /*
         * verify
         */
        assertThat(notice.handled()).isTrue();
        assertThat(notice.shouldSend()).isFalse();
        assertThat(party.getMeetingNotifiedAt()).isEqualTo(MEETING_AT);
    }

    // ───────────────────────── DEPARTURE_REMINDER_OFFSETS ─────────────────────────

    @DisplayName("DEPARTURE_REMINDER_OFFSETS - 약속 시각 뒤 30분, 1시간, 2시간, 4시간 네 번이다")
    @Test
    void departureReminderOffsets() {

        /*
         * verify
         */
        assertThat(Party.DEPARTURE_REMINDER_OFFSETS).containsExactly(
                Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(4));
    }

    // ───────────────────────── advanceDepartureReminder ─────────────────────────

    @DisplayName("advanceDepartureReminder - 차례 시각이 되면 차례를 하나 넘기고 출발을 누를 수 있는 방장에게 보낸다. version 은 그대로다")
    @ParameterizedTest(name = "차례 {0} (+{1}분)")
    @CsvSource({"0, 30", "1, 60", "2, 120", "3, 240"})
    void advanceDepartureReminder(int count, long minutes) {

        /*
         * settings
         */
        Party party = confirmedParty(PartyStatus.COMPLETED, MEETING_AT, count, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1), arrived(THIRD_ID, 2));

        /*
         * test
         */
        PartyNotice notice = party.advanceDepartureReminder(MEETING_AT.plus(Duration.ofMinutes(minutes)));

        /*
         * verify
         */
        assertThat(notice.shouldSend()).isTrue();
        assertThat(notice.recipientIds()).containsExactly(HOST_ID);
        assertThat(party.getDepartureReminderCount()).isEqualTo(count + 1);
        assertThat(party.getVersion()).isEqualTo(VERSION);
    }

    @DisplayName("advanceDepartureReminder - 차례 시각 1초 전이면 차례가 아니고 아무것도 바꾸지 않는다")
    @ParameterizedTest(name = "차례 {0} (+{1}분)")
    @CsvSource({"0, 30", "1, 60", "2, 120", "3, 240"})
    void advanceDepartureReminder_notDue(int count, long minutes) {

        /*
         * settings
         */
        Party party = confirmedParty(PartyStatus.COMPLETED, MEETING_AT, count, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1));

        /*
         * test
         */
        PartyNotice notice = party.advanceDepartureReminder(MEETING_AT.plus(Duration.ofMinutes(minutes)).minusSeconds(1));

        /*
         * verify
         */
        assertThat(notice.handled()).isFalse();
        assertThat(party.getDepartureReminderCount()).isEqualTo(count);
    }

    @DisplayName("advanceDepartureReminder - 방장이 아직 도착 전이어도 방장에게 보낸다 - 도착 뒤 출발을 눌러야 하는 사람이다")
    @Test
    void advanceDepartureReminder_hostNotArrived() {

        /*
         * settings
         */
        Party party = confirmedParty(PartyStatus.COMPLETED, MEETING_AT, 0, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));

        /*
         * test
         */
        PartyNotice notice = party.advanceDepartureReminder(MEETING_AT.plus(Duration.ofMinutes(30)));

        /*
         * verify
         */
        assertThat(notice.shouldSend()).isTrue();
        assertThat(notice.recipientIds()).containsExactly(HOST_ID);
        assertThat(party.getDepartureReminderCount()).isEqualTo(1);
    }

    @DisplayName("advanceDepartureReminder - 네 번을 다 처리했으면 더는 차례가 없다")
    @Test
    void advanceDepartureReminder_stopsAtFour() {

        /*
         * settings
         */
        Party party = confirmedParty(PartyStatus.COMPLETED, MEETING_AT, 4, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1));

        /*
         * test
         */
        PartyNotice notice = party.advanceDepartureReminder(MEETING_AT.plus(Duration.ofHours(10)));

        /*
         * verify
         */
        assertThat(notice.handled()).isFalse();
        assertThat(party.getDepartureReminderCount()).isEqualTo(4);
    }

    @DisplayName("advanceDepartureReminder - 서버가 멈춰 여러 차례가 밀렸으면 지난 차례를 모두 넘기고 한 번만 보낸다")
    @ParameterizedTest(name = "+{0}분 → 차례 {1}")
    @CsvSource({"150, 3", "300, 4"})
    void advanceDepartureReminder_catchUp(long minutes, int expectedCount) {

        /*
         * settings
         */
        Party party = confirmedParty(PartyStatus.COMPLETED, MEETING_AT, 0, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1));

        /*
         * test
         */
        PartyNotice notice = party.advanceDepartureReminder(MEETING_AT.plus(Duration.ofMinutes(minutes)));

        /*
         * verify
         */
        assertThat(notice.shouldSend()).isTrue();
        assertThat(notice.recipientIds()).containsExactly(HOST_ID);
        assertThat(party.getDepartureReminderCount()).isEqualTo(expectedCount);
    }

    @DisplayName("advanceDepartureReminder - 닫힌 방은 차례가 아니다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED", "CANCELED"})
    void advanceDepartureReminder_closed(PartyStatus status) {

        /*
         * settings
         */
        Party party = confirmedParty(status, MEETING_AT, 0, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1));

        /*
         * test
         */
        PartyNotice notice = party.advanceDepartureReminder(MEETING_AT.plus(Duration.ofHours(1)));

        /*
         * verify
         */
        assertThat(notice.handled()).isFalse();
        assertThat(party.getDepartureReminderCount()).isZero();
    }

    @DisplayName("advanceDepartureReminder - 혼자 남은 방(멤버 1명)은 도착했어도 출발할 수 없어 보내지 않고 차례만 넘긴다")
    @Test
    void advanceDepartureReminder_alone() {

        /*
         * settings
         */
        Party party = party(RoomType.RESERVED, MEETING_AT, PartyStatus.ACTIVE, MEETING_AT, 0, arrived(HOST_ID, 0));

        /*
         * test
         */
        PartyNotice notice = party.advanceDepartureReminder(MEETING_AT.plus(Duration.ofMinutes(30)));

        /*
         * verify
         */
        assertThat(notice.handled()).isTrue();
        assertThat(notice.shouldSend()).isFalse();
        assertThat(party.getDepartureReminderCount()).isEqualTo(1);
    }

    // ───────────────────────── 도우미 ─────────────────────────

    private static Party confirmedParty(PartyStatus status, Instant meetingNotifiedAt, int departureReminderCount, PartyMember... members) {
        return party(RoomType.REALTIME, MEETING_AT, status, meetingNotifiedAt, departureReminderCount, members);
    }

    private static Party party(RoomType roomType, Instant departureAt, PartyStatus status, Instant meetingNotifiedAt, int departureReminderCount,
                               PartyMember... members) {
        return restored(roomType, status, departureAt, meetingNotifiedAt, departureReminderCount, members);
    }
}
