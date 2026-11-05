package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.MeetingNotifier;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.RoomType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MeetingNotificationServiceUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long HOST_ID = 1L;
    private static final Long MEMBER_ID = 2L;
    private static final Long THIRD_ID = 3L;
    private static final Location DEPARTURE = new Location(37.4979, 127.0276);
    private static final Location DESTINATION = new Location(37.3948, 127.1112);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 한국 시각 낮 12:00 - 방해 금지 시간이 아니다
    private static final Instant MEETING_AT = seoul("2026-11-03T12:00:00");
    private static final String MEETING_TASK = "party.meeting-notice";
    private static final String REMINDER_TASK = "party.departure-reminder";
    private static final String NO_SHOW_TASK = "party.no-show-notice";

    @Mock
    private Parties parties;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;
    @Mock
    private MeetingNotifier meetingNotifier;

    @InjectMocks
    private MeetingNotificationService service;

    // ───────────────────────── notifyMeetingTime ─────────────────────────

    @DisplayName("notifyMeetingTime - 잠근 방에 처리 시각을 남겨 저장하고, 커밋 후 멤버 전원에게 약속 시각 알림을 보낸다")
    @Test
    void notifyMeetingTime() {

        /*
         * settings
         */
        Party party = confirmedParty(MEETING_AT, PartyStatus.COMPLETED, null, 0, waiting(HOST_ID, 0), arrived(MEMBER_ID, 1), waiting(THIRD_ID, 2));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));
        Instant now = MEETING_AT.plusSeconds(20);

        /*
         * test
         */
        service.notifyMeetingTime(PARTY_ID, now);

        /*
         * verify
         */
        assertThat(party.getMeetingNotifiedAt()).isEqualTo(now);
        var order = inOrder(parties, afterCommitExecutor);
        order.verify(parties).findByIdForUpdate(eq(PARTY_ID));
        order.verify(parties).save(eq(party));
        order.verify(afterCommitExecutor).execute(eq(MEETING_TASK), any());

        Runnable task = capturedTask(MEETING_TASK);
        verifyNoInteractions(meetingNotifier);
        task.run();
        verify(meetingNotifier).notifyMeetingTime(eq(PARTY_ID), eq(List.of(HOST_ID, MEMBER_ID, THIRD_ID)));
    }

    @DisplayName("notifyMeetingTime - 방해 금지 시간이어도 보낸다(약속 자체가 밤일 수 있다)")
    @ParameterizedTest(name = "{0} KST")
    @CsvSource({"2026-11-03T23:00:00", "2026-11-04T02:00:00", "2026-11-04T07:59:00"})
    void notifyMeetingTime_quietHours(LocalDateTime meetingAt) {

        /*
         * settings
         */
        Instant meeting = meetingAt.atZone(SEOUL).toInstant();
        Party party = confirmedParty(meeting, PartyStatus.COMPLETED, null, 0, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.notifyMeetingTime(PARTY_ID, meeting);

        /*
         * verify
         */
        capturedTask(MEETING_TASK).run();
        verify(meetingNotifier).notifyMeetingTime(eq(PARTY_ID), eq(List.of(HOST_ID, MEMBER_ID)));
    }

    @DisplayName("notifyMeetingTime - 약속 시각에서 30분 넘게 지났으면 처리만 저장하고 보내지 않는다")
    @Test
    void notifyMeetingTime_stale() {

        /*
         * settings
         */
        Party party = confirmedParty(MEETING_AT, PartyStatus.COMPLETED, null, 0, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));
        Instant now = MEETING_AT.plus(Duration.ofMinutes(31));

        /*
         * test
         */
        service.notifyMeetingTime(PARTY_ID, now);

        /*
         * verify
         */
        assertThat(party.getMeetingNotifiedAt()).isEqualTo(now);
        verify(parties).save(eq(party));
        verifyNoInteractions(afterCommitExecutor, meetingNotifier);
    }

    @DisplayName("notifyMeetingTime - 같은 방을 두 번 처리해도 알림은 한 번만 맡긴다")
    @Test
    void notifyMeetingTime_once() {

        /*
         * settings
         */
        Party party = confirmedParty(MEETING_AT, PartyStatus.COMPLETED, null, 0, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.notifyMeetingTime(PARTY_ID, MEETING_AT);
        service.notifyMeetingTime(PARTY_ID, MEETING_AT.plus(Duration.ofMinutes(1)));

        /*
         * verify
         */
        assertThat(party.getMeetingNotifiedAt()).isEqualTo(MEETING_AT);
        verify(parties, times(1)).save(any());
        verify(afterCommitExecutor, times(1)).execute(eq(MEETING_TASK), any());
    }

    @DisplayName("notifyMeetingTime - 이미 처리한 방은 저장도 알림도 없다")
    @Test
    void notifyMeetingTime_alreadyNotified() {

        /*
         * settings
         */
        Party party = confirmedParty(MEETING_AT, PartyStatus.COMPLETED, MEETING_AT, 0, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.notifyMeetingTime(PARTY_ID, MEETING_AT.plus(Duration.ofMinutes(1)));

        /*
         * verify
         */
        verify(parties, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, meetingNotifier);
    }

    @DisplayName("notifyMeetingTime - 약속을 확정하지 않은 실시간 방은 저장도 알림도 없다")
    @Test
    void notifyMeetingTime_unconfirmed() {

        /*
         * settings
         */
        Party party = confirmedParty(null, PartyStatus.COMPLETED, null, 0, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.notifyMeetingTime(PARTY_ID, MEETING_AT);

        /*
         * verify
         */
        assertThat(party.getMeetingNotifiedAt()).isNull();
        verify(parties, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, meetingNotifier);
    }

    @DisplayName("notifyMeetingTime - 조회와 잠금 사이에 닫힌 방은 저장도 알림도 없다")
    @ParameterizedTest(name = "{0}")
    @CsvSource({"FINISHED", "CANCELED"})
    void notifyMeetingTime_closed(PartyStatus status) {

        /*
         * settings
         */
        Party party = confirmedParty(MEETING_AT, status, null, 0, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.notifyMeetingTime(PARTY_ID, MEETING_AT);

        /*
         * verify
         */
        verify(parties, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, meetingNotifier);
    }

    @DisplayName("notifyMeetingTime - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void notifyMeetingTime_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.notifyMeetingTime(PARTY_ID, MEETING_AT));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(MatchingErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(afterCommitExecutor, meetingNotifier);
    }

    // ───────────────────────── remindDeparture ─────────────────────────

    @DisplayName("remindDeparture - 차례를 넘겨 저장하고, 커밋 후 도착한 멤버에게만 출발 재촉을 보낸다")
    @Test
    void remindDeparture() {

        /*
         * settings
         */
        Party party = confirmedParty(MEETING_AT, PartyStatus.COMPLETED, MEETING_AT, 0, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1), arrived(THIRD_ID, 2));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.remindDeparture(PARTY_ID, MEETING_AT.plus(Duration.ofMinutes(30)));

        /*
         * verify
         */
        assertThat(party.getDepartureReminderCount()).isEqualTo(1);
        var order = inOrder(parties, afterCommitExecutor);
        order.verify(parties).findByIdForUpdate(eq(PARTY_ID));
        order.verify(parties).save(eq(party));
        order.verify(afterCommitExecutor).execute(eq(REMINDER_TASK), any());

        Runnable task = capturedTask(REMINDER_TASK);
        verifyNoInteractions(meetingNotifier);
        task.run();
        verify(meetingNotifier).remindDeparture(eq(PARTY_ID), eq(List.of(HOST_ID, THIRD_ID)));
    }

    @DisplayName("remindDeparture - 도착한 멤버가 없으면 차례만 넘겨 저장하고 보내지 않는다")
    @Test
    void remindDeparture_nobodyArrived() {

        /*
         * settings
         */
        Party party = confirmedParty(MEETING_AT, PartyStatus.COMPLETED, MEETING_AT, 0, waiting(HOST_ID, 0), waiting(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.remindDeparture(PARTY_ID, MEETING_AT.plus(Duration.ofMinutes(30)));

        /*
         * verify
         */
        assertThat(party.getDepartureReminderCount()).isEqualTo(1);
        verify(parties).save(eq(party));
        verifyNoInteractions(afterCommitExecutor, meetingNotifier);
    }

    @DisplayName("remindDeparture - 방해 금지 시간(23:00~08:00 KST)에 차례가 되면 차례만 넘기고 보내지 않는다")
    @ParameterizedTest(name = "{0} KST → 보냄 {1}")
    @CsvSource({
            "2026-11-03T22:59:00, true",
            "2026-11-03T23:00:00, false",
            "2026-11-04T07:59:00, false",
            "2026-11-04T08:00:00, true"})
    void remindDeparture_quietHours(LocalDateTime seoulNow, boolean sends) {

        /*
         * settings
         */
        Instant now = seoulNow.atZone(SEOUL).toInstant();
        Instant meetingAt = now.minus(Duration.ofMinutes(30));
        Party party = confirmedParty(meetingAt, PartyStatus.COMPLETED, meetingAt, 0, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.remindDeparture(PARTY_ID, now);

        /*
         * verify
         */
        assertThat(party.getDepartureReminderCount()).isEqualTo(1);
        verify(parties).save(eq(party));
        verify(afterCommitExecutor, times(sends ? 1 : 0)).execute(eq(REMINDER_TASK), any());
        verifyNoInteractions(meetingNotifier);
    }

    @DisplayName("remindDeparture - 네 번을 다 처리한 방은 저장도 알림도 없다")
    @Test
    void remindDeparture_exhausted() {

        /*
         * settings
         */
        Party party = confirmedParty(MEETING_AT, PartyStatus.COMPLETED, MEETING_AT, 4, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.remindDeparture(PARTY_ID, MEETING_AT.plus(Duration.ofHours(6)));

        /*
         * verify
         */
        assertThat(party.getDepartureReminderCount()).isEqualTo(4);
        verify(parties, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, meetingNotifier);
    }

    @DisplayName("remindDeparture - 차례 시각 전이면 저장도 알림도 없다")
    @Test
    void remindDeparture_notDue() {

        /*
         * settings
         */
        Party party = confirmedParty(MEETING_AT, PartyStatus.COMPLETED, MEETING_AT, 1, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.remindDeparture(PARTY_ID, MEETING_AT.plus(Duration.ofMinutes(59)));

        /*
         * verify
         */
        assertThat(party.getDepartureReminderCount()).isEqualTo(1);
        verify(parties, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, meetingNotifier);
    }

    @DisplayName("remindDeparture - 출발·자동 정리로 닫힌 방은 저장도 알림도 없다(재촉이 끝난다)")
    @ParameterizedTest(name = "{0}")
    @CsvSource({"FINISHED", "CANCELED"})
    void remindDeparture_closed(PartyStatus status) {

        /*
         * settings
         */
        Party party = confirmedParty(MEETING_AT, status, MEETING_AT, 1, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1));
        doReturn(Optional.of(party)).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.remindDeparture(PARTY_ID, MEETING_AT.plus(Duration.ofHours(1)));

        /*
         * verify
         */
        verify(parties, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, meetingNotifier);
    }

    @DisplayName("remindDeparture - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void remindDeparture_partyNotFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findByIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.remindDeparture(PARTY_ID, MEETING_AT));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(MatchingErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(afterCommitExecutor, meetingNotifier);
    }

    // ───────────────────────── notifyNoShows ─────────────────────────

    @DisplayName("notifyNoShows - 커밋 후에 노쇼 멤버에게 노쇼 알림을 보낸다")
    @Test
    void notifyNoShows() {

        /*
         * test
         */
        service.notifyNoShows(PARTY_ID, List.of(MEMBER_ID, THIRD_ID), MEETING_AT.plus(Duration.ofMinutes(10)));

        /*
         * verify
         */
        Runnable task = capturedTask(NO_SHOW_TASK);
        verifyNoInteractions(meetingNotifier);
        task.run();
        verify(meetingNotifier).notifyNoShow(eq(PARTY_ID), eq(List.of(MEMBER_ID, THIRD_ID)));
        verifyNoInteractions(parties);
    }

    @DisplayName("notifyNoShows - 방해 금지 시간(23:00~08:00 KST)에는 보내지 않는다")
    @ParameterizedTest(name = "{0} KST → 보냄 {1}")
    @CsvSource({
            "2026-11-03T22:59:00, true",
            "2026-11-03T23:00:00, false",
            "2026-11-04T07:59:00, false",
            "2026-11-04T08:00:00, true"})
    void notifyNoShows_quietHours(LocalDateTime seoulNow, boolean sends) {

        /*
         * test
         */
        service.notifyNoShows(PARTY_ID, List.of(MEMBER_ID), seoulNow.atZone(SEOUL).toInstant());

        /*
         * verify
         */
        verify(afterCommitExecutor, times(sends ? 1 : 0)).execute(eq(NO_SHOW_TASK), any());
        verifyNoInteractions(meetingNotifier);
    }

    @DisplayName("notifyNoShows - 노쇼가 없으면 아무것도 맡기지 않는다")
    @Test
    void notifyNoShows_empty() {

        /*
         * test
         */
        service.notifyNoShows(PARTY_ID, List.of(), MEETING_AT);

        /*
         * verify
         */
        verify(afterCommitExecutor, never()).execute(anyString(), any());
        verifyNoInteractions(meetingNotifier);
    }

    // ───────────────────────── 도우미 ─────────────────────────

    private static Instant seoul(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(SEOUL).toInstant();
    }

    /** departureAt 으로 약속을 확정한 실시간 방(null 이면 확정 전). members 앞쪽이 먼저 합류했고 첫 번째가 방장 */
    private static Party confirmedParty(Instant departureAt, PartyStatus status, Instant meetingNotifiedAt, int departureReminderCount, PartyMember... members) {
        return Party.load(PARTY_ID, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(3),
                new ArrayList<>(List.of(members)), members[0].getMemberId(), MEETING_AT.minus(Duration.ofDays(1)), status, 12000, 25, "encoded-path",
                5L, RoomType.REALTIME, departureAt, null, meetingNotifiedAt, departureReminderCount);
    }

    private static PartyMember waiting(Long memberId, int order) {
        return PartyMember.load(memberId, MEETING_AT.minus(Duration.ofDays(1)).plusSeconds(order));
    }

    private static PartyMember arrived(Long memberId, int order) {
        return PartyMember.load(memberId, MEETING_AT.minus(Duration.ofDays(1)).plusSeconds(order), MEETING_AT.minus(Duration.ofDays(1)).plusSeconds(60), null, null);
    }

    /** 알림은 커밋 후 실행기에 맡긴 작업 안에서만 나가야 한다 */
    private Runnable capturedTask(String name) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommitExecutor).execute(eq(name), captor.capture());

        return captor.getValue();
    }
}
