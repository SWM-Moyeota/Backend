package team.codingforest.moyeota.matching.party;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.exception.MatchingErrorCode;
import team.codingforest.moyeota.matching.party.domain.Parties;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MeetingNotificationSweeperUnitTest {
    @Mock
    private Parties parties;
    @Mock
    private MeetingNotificationService notificationService;

    @InjectMocks
    private MeetingNotificationSweeper sweeper;

    @DisplayName("sweep - 한 번 돌 때의 시각 하나로 대상을 찾고 같은 시각을 방마다 서비스에 넘긴다")
    @Test
    void sweep() {

        /*
         * settings
         */
        doReturn(List.of(1L, 2L)).when(parties).findMeetingNoticeTargets(any());
        doReturn(List.of(3L)).when(parties).findDepartureReminderTargets(any());
        Instant before = Instant.now();

        /*
         * test
         */
        sweeper.sweep();

        /*
         * verify
         */
        Instant after = Instant.now();
        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        verify(parties).findMeetingNoticeTargets(now.capture());
        assertThat(now.getValue()).isBetween(before, after);
        verify(parties).findDepartureReminderTargets(eq(now.getValue()));
        verify(notificationService).notifyMeetingTime(eq(1L), eq(now.getValue()));
        verify(notificationService).notifyMeetingTime(eq(2L), eq(now.getValue()));
        verify(notificationService).remindDeparture(eq(3L), eq(now.getValue()));
    }

    @DisplayName("sweep - 한 방이 실패해도(업무 예외·DB 예외) 나머지 방과 출발 재촉을 계속 처리한다")
    @Test
    void sweep_continuesAfterFailure() {

        /*
         * settings
         */
        doReturn(List.of(1L, 2L, 3L)).when(parties).findMeetingNoticeTargets(any());
        doReturn(List.of(4L, 5L)).when(parties).findDepartureReminderTargets(any());
        doThrow(new BusinessException(MatchingErrorCode.PARTY_NOT_FOUND)).when(notificationService).notifyMeetingTime(eq(1L), any());
        doThrow(new PessimisticLockingFailureException("잠금 대기 시간 초과")).when(notificationService).notifyMeetingTime(eq(2L), any());
        doThrow(new IllegalStateException("알 수 없는 실패")).when(notificationService).remindDeparture(eq(4L), any());

        /*
         * test
         */
        sweeper.sweep();

        /*
         * verify
         */
        verify(notificationService).notifyMeetingTime(eq(3L), any());
        verify(notificationService).remindDeparture(eq(4L), any());
        verify(notificationService).remindDeparture(eq(5L), any());
    }

    @DisplayName("sweep - 대상이 없으면 서비스를 부르지 않는다")
    @Test
    void sweep_noTargets() {

        /*
         * settings
         */
        doReturn(List.of()).when(parties).findMeetingNoticeTargets(any());
        doReturn(List.of()).when(parties).findDepartureReminderTargets(any());

        /*
         * test
         */
        sweeper.sweep();

        /*
         * verify
         */
        verify(notificationService, never()).notifyMeetingTime(anyLong(), any());
        verify(notificationService, never()).remindDeparture(anyLong(), any());
    }
}
