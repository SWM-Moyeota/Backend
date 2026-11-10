package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Capacity;
import team.codingforest.moyeota.matching.party.domain.Location;
import team.codingforest.moyeota.matching.party.domain.MeetingNotifier;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;
import team.codingforest.moyeota.matching.party.domain.PartyMember;
import team.codingforest.moyeota.matching.party.domain.enums.PartyStatus;
import team.codingforest.moyeota.matching.party.domain.enums.RoomType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 *  약속 시각 알림·출발 재촉 - 언제 보낼지는 스케줄러가 고르고, 여기서는 방을 읽어 Party 가 정한 받는 사람에게 보낸다.
 *  방을 바꾸지 않으므로 잠금·저장이 없다
 */
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

    @Mock
    private Parties parties;
    @Mock
    private MeetingNotifier meetingNotifier;

    @InjectMocks
    private MeetingNotificationService service;

    // ───────────────────────── notifyMeetingTime ─────────────────────────

    @DisplayName("notifyMeetingTime - 열린 확정 방의 멤버 전원(합류 순)에게 약속 시각 알림을 보내고, 잠그거나 저장하지 않는다")
    @Test
    void notifyMeetingTime() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.COMPLETED, waiting(HOST_ID, 0), arrived(MEMBER_ID, 1), waiting(THIRD_ID, 2))))
                .when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        service.notifyMeetingTime(PARTY_ID);

        /*
         * verify
         */
        verify(meetingNotifier).notifyMeetingTime(eq(PARTY_ID), eq(List.of(HOST_ID, MEMBER_ID, THIRD_ID)));
        verify(parties).findById(eq(PARTY_ID));
        verifyNoMoreInteractions(parties);
    }

    @DisplayName("notifyMeetingTime - 혼자인 방은 보내지 않는다")
    @Test
    void notifyMeetingTime_alone() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.COMPLETED, waiting(HOST_ID, 0)))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        service.notifyMeetingTime(PARTY_ID);

        /*
         * verify
         */
        verifyNoInteractions(meetingNotifier);
    }

    @DisplayName("notifyMeetingTime - 조회와 실행 사이에 닫힌 방은 보내지 않는다")
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = PartyStatus.class, names = {"FINISHED", "CANCELED"})
    void notifyMeetingTime_closed(PartyStatus status) {

        /*
         * settings
         */
        doReturn(Optional.of(party(status, arrived(HOST_ID, 0), waiting(MEMBER_ID, 1)))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        service.notifyMeetingTime(PARTY_ID);

        /*
         * verify
         */
        verifyNoInteractions(meetingNotifier);
    }

    @DisplayName("notifyMeetingTime - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void notifyMeetingTime_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.notifyMeetingTime(PARTY_ID));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(MatchingErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(meetingNotifier);
    }

    // ───────────────────────── remindDeparture ─────────────────────────

    @DisplayName("remindDeparture - 출발 버튼을 누르는 방장 한 명에게 출발 재촉을 보낸다")
    @Test
    void remindDeparture() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.COMPLETED, waiting(HOST_ID, 0), arrived(MEMBER_ID, 1)))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        service.remindDeparture(PARTY_ID, MEETING_AT.plus(Duration.ofMinutes(30)));

        /*
         * verify
         */
        verify(meetingNotifier).remindDeparture(eq(PARTY_ID), eq(List.of(HOST_ID)));
        verify(meetingNotifier, never()).notifyMeetingTime(eq(PARTY_ID), eq(List.of(HOST_ID)));
    }

    @DisplayName("remindDeparture - 방해 금지 시간(23:00~08:00 KST)이면 보내지 않는다")
    @ParameterizedTest(name = "{0} KST → 보냄 {1}")
    @CsvSource({"2026-11-03T22:59:59, true", "2026-11-03T23:00:00, false", "2026-11-04T07:59:59, false", "2026-11-04T08:00:00, true"})
    void remindDeparture_quietHours(String localNow, boolean sends) {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.COMPLETED, waiting(HOST_ID, 0), arrived(MEMBER_ID, 1)))).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        service.remindDeparture(PARTY_ID, seoul(localNow));

        /*
         * verify
         */
        if(sends) verify(meetingNotifier).remindDeparture(eq(PARTY_ID), eq(List.of(HOST_ID)));
        else verifyNoInteractions(meetingNotifier);
    }

    @DisplayName("remindDeparture - 혼자인 방과 닫힌 방은 보내지 않는다")
    @Test
    void remindDeparture_notDue() {

        /*
         * settings
         */
        doReturn(Optional.of(party(PartyStatus.COMPLETED, arrived(HOST_ID, 0)))).when(parties).findById(eq(PARTY_ID));
        doReturn(Optional.of(party(PartyStatus.FINISHED, arrived(HOST_ID, 0), arrived(MEMBER_ID, 1)))).when(parties).findById(eq(PARTY_ID + 1));

        /*
         * test
         */
        service.remindDeparture(PARTY_ID, MEETING_AT.plus(Duration.ofMinutes(30)));
        service.remindDeparture(PARTY_ID + 1, MEETING_AT.plus(Duration.ofMinutes(30)));

        /*
         * verify
         */
        verifyNoInteractions(meetingNotifier);
    }

    @DisplayName("remindDeparture - 없는 방이면 PARTY_NOT_FOUND")
    @Test
    void remindDeparture_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(parties).findById(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.remindDeparture(PARTY_ID, MEETING_AT));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(MatchingErrorCode.PARTY_NOT_FOUND);
        verifyNoInteractions(meetingNotifier);
    }

    // ───────────────────────── 도우미 ─────────────────────────

    private static Instant seoul(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(SEOUL).toInstant();
    }

    /** MEETING_AT 으로 약속을 확정한 실시간 방. members 앞쪽이 먼저 합류했고 첫 번째가 방장 */
    private static Party party(PartyStatus status, PartyMember... members) {
        return Party.load(PARTY_ID, DEPARTURE, DESTINATION, "강남역", "판교역", new Capacity(3),
                new ArrayList<>(List.of(members)), members[0].getMemberId(), MEETING_AT.minus(Duration.ofDays(1)), status, 12000, 25, "encoded-path",
                5L, RoomType.REALTIME, MEETING_AT);
    }

    private static PartyMember waiting(Long memberId, int order) {
        return PartyMember.load(memberId, MEETING_AT.minus(Duration.ofDays(1)).plusSeconds(order), null);
    }

    private static PartyMember arrived(Long memberId, int order) {
        return PartyMember.load(memberId, MEETING_AT.minus(Duration.ofDays(1)).plusSeconds(order), MEETING_AT.minus(Duration.ofMinutes(5)));
    }
}
