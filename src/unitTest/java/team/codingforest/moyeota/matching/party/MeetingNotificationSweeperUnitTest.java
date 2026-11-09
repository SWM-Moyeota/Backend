package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Parties;
import team.codingforest.moyeota.matching.party.domain.Party;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 *  매분 0초에 돌아 [직전 분, 이번 분) 에 차례가 된 방만 고른다 - 약속 시각 알림은 departure_at 이 그 구간인 방,
 *  출발 재촉 차례 i 는 departure_at 이 그 구간을 OFFSETS[i] 만큼 당긴 곳인 방
 */
@ExtendWith(MockitoExtension.class)
class MeetingNotificationSweeperUnitTest {
    @Mock
    private Parties parties;
    @Mock
    private MeetingNotificationService notificationService;

    @InjectMocks
    private MeetingNotificationSweeper sweeper;

    @DisplayName("sweep - 실행한 분을 끝으로 하는 1분 구간 하나로 약속 시각 알림을, 그 구간을 재촉 간격만큼 당긴 네 구간으로 출발 재촉을 고른다")
    @Test
    void sweep_windows() {

        /*
         * settings
         */
        doReturn(List.of()).when(parties).findOpenConfirmedIdsDepartingBetween(any(), any());
        Instant before = Instant.now();

        /*
         * test
         */
        sweeper.sweep();

        /*
         * verify
         */
        Instant after = Instant.now();
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(parties, times(1 + Party.DEPARTURE_REMINDER_OFFSETS.size())).findOpenConfirmedIdsDepartingBetween(from.capture(), to.capture());

        Instant meetingTo = to.getAllValues().get(0);
        assertThat(meetingTo).as("실행한 분의 0초").isEqualTo(meetingTo.truncatedTo(ChronoUnit.MINUTES))
                .isBetween(before.truncatedTo(ChronoUnit.MINUTES), after.truncatedTo(ChronoUnit.MINUTES));
        assertThat(from.getAllValues().get(0)).isEqualTo(meetingTo.minus(Duration.ofMinutes(1)));

        for(int i = 0; i < Party.DEPARTURE_REMINDER_OFFSETS.size(); i++) {
            Duration offset = Party.DEPARTURE_REMINDER_OFFSETS.get(i);
            assertThat(from.getAllValues().get(i + 1)).as("재촉 %d번째 구간 시작", i).isEqualTo(meetingTo.minus(Duration.ofMinutes(1)).minus(offset));
            assertThat(to.getAllValues().get(i + 1)).as("재촉 %d번째 구간 끝", i).isEqualTo(meetingTo.minus(offset));
        }
    }

    @DisplayName("sweep - 약속 시각 구간의 방에는 약속 시각 알림을, 재촉 구간의 방에는 실행 시각과 함께 출발 재촉을 맡긴다")
    @Test
    void sweep_dispatch() {

        /*
         * settings
         */
        // 부르는 순서: 약속 시각 구간, 재촉 30분·1시간·2시간·4시간 구간
        doReturn(List.of(1L, 2L)).doReturn(List.of(3L)).doReturn(List.of()).doReturn(List.of(4L)).doReturn(List.of())
                .when(parties).findOpenConfirmedIdsDepartingBetween(any(), any());
        Instant before = Instant.now();

        /*
         * test
         */
        sweeper.sweep();

        /*
         * verify
         */
        Instant after = Instant.now();
        verify(notificationService).notifyMeetingTime(1L);
        verify(notificationService).notifyMeetingTime(2L);
        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        verify(notificationService).remindDeparture(eq(3L), now.capture());
        verify(notificationService).remindDeparture(eq(4L), eq(now.getValue()));
        assertThat(now.getValue()).isBetween(before, after);
        verify(notificationService, never()).notifyMeetingTime(3L);
    }

    @DisplayName("sweep - 한 방이 실패해도(업무 예외·DB 예외) 나머지 방과 출발 재촉을 계속 처리한다")
    @Test
    void sweep_continuesAfterFailure() {

        /*
         * settings
         */
        doReturn(List.of(1L, 2L, 3L)).doReturn(List.of(4L, 5L)).doReturn(List.of())
                .when(parties).findOpenConfirmedIdsDepartingBetween(any(), any());
        doThrow(new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND)).when(notificationService).notifyMeetingTime(1L);
        doThrow(new DataAccessResourceFailureException("연결 끊김")).when(notificationService).notifyMeetingTime(2L);
        doThrow(new IllegalStateException("알 수 없는 실패")).when(notificationService).remindDeparture(eq(4L), any());

        /*
         * test
         */
        sweeper.sweep();

        /*
         * verify
         */
        verify(notificationService).notifyMeetingTime(3L);
        verify(notificationService).remindDeparture(eq(5L), any());
    }

    @DisplayName("sweep - 대상이 없으면 서비스를 부르지 않는다")
    @Test
    void sweep_noTargets() {

        /*
         * settings
         */
        doReturn(List.of()).when(parties).findOpenConfirmedIdsDepartingBetween(any(), any());

        /*
         * test
         */
        sweeper.sweep();

        /*
         * verify
         */
        verify(notificationService, never()).notifyMeetingTime(anyLong());
        verify(notificationService, never()).remindDeparture(anyLong(), any());
    }
}
