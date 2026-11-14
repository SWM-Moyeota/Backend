package team.codingforest.moyeota.faresplit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.faresplit.domain.FareSplits;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class FareSplitSweeperUnitTest {
    // 한국 시각 11-04 12:00
    private static final Instant NOON = Instant.parse("2026-11-04T03:00:00Z");
    // 한국 시각 11-05 02:30
    private static final Instant NIGHT = Instant.parse("2026-11-04T17:30:00Z");

    @Mock
    private FareSplits fareSplits;
    @Mock
    private FareSplitService fareSplitService;
    @Mock
    private FareSplitNotificationService notificationService;

    @InjectMocks
    private FareSplitSweeper sweeper;

    @DisplayName("sweepAt - 한 번 돌 때의 시각 하나로 대상을 찾아 만료 → 송금 재요청 두 단계만 순서대로 정산마다 서비스에 넘긴다")
    @Test
    void sweepAt() {

        /*
         * settings
         */
        doReturn(List.of(1L)).when(fareSplits).findExpireTargets(any());
        doReturn(List.of(2L, 3L)).when(fareSplits).findTransferRequestTargets(any());

        /*
         * test
         */
        sweeper.sweepAt(NOON);

        /*
         * verify
         */
        InOrder order = inOrder(fareSplits, fareSplitService, notificationService);
        order.verify(fareSplits).findExpireTargets(eq(NOON));
        order.verify(fareSplitService).expire(eq(1L), eq(NOON));
        order.verify(fareSplits).findTransferRequestTargets(eq(NOON));
        order.verify(notificationService).remindTransfers(eq(2L), eq(NOON));
        order.verify(notificationService).remindTransfers(eq(3L), eq(NOON));
        verifyNoMoreInteractions(fareSplits, fareSplitService, notificationService);
    }

    @DisplayName("sweepAt - 한 정산이 실패해도(업무 예외·DB 예외·그 밖의 런타임 예외) 나머지 정산과 다음 단계를 계속한다")
    @Test
    void sweepAt_continuesAfterFailure() {

        /*
         * settings
         */
        doReturn(List.of(1L, 2L, 3L)).when(fareSplits).findExpireTargets(any());
        doReturn(List.of(4L, 5L)).when(fareSplits).findTransferRequestTargets(any());
        doThrow(new PessimisticLockingFailureException("잠금 대기 시간 초과")).when(fareSplitService).expire(eq(1L), any());
        doThrow(new IllegalStateException("알 수 없는 실패")).when(fareSplitService).expire(eq(2L), any());
        doThrow(new BusinessException(FareSplitErrorCode.FARE_SPLIT_NOT_FOUND)).when(notificationService).remindTransfers(eq(4L), any());

        /*
         * test
         */
        sweeper.sweepAt(NOON);

        /*
         * verify
         */
        verify(fareSplitService).expire(eq(3L), eq(NOON));
        verify(notificationService).remindTransfers(eq(4L), eq(NOON));
        verify(notificationService).remindTransfers(eq(5L), eq(NOON));
    }

    @DisplayName("sweepAt - 새벽에도 만료와 송금 재요청을 모두 처리한다")
    @Test
    void sweepAt_night() {

        /*
         * settings
         */
        doReturn(List.of(1L)).when(fareSplits).findExpireTargets(any());
        doReturn(List.of(2L)).when(fareSplits).findTransferRequestTargets(any());

        /*
         * test
         */
        sweeper.sweepAt(NIGHT);

        /*
         * verify
         */
        verify(fareSplitService).expire(eq(1L), eq(NIGHT));
        verify(notificationService).remindTransfers(eq(2L), eq(NIGHT));
    }

    @DisplayName("sweep - 지금 시각으로 돈다")
    @Test
    void sweep() {

        /*
         * settings
         */
        doReturn(List.of()).when(fareSplits).findExpireTargets(any());
        doReturn(List.of()).when(fareSplits).findTransferRequestTargets(any());
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
        verify(fareSplits).findExpireTargets(now.capture());
        assertThat(now.getValue()).isBetween(before, after);
        verify(fareSplits).findTransferRequestTargets(eq(now.getValue()));
        verify(fareSplitService, never()).expire(anyLong(), any());
        verify(notificationService, never()).remindTransfers(anyLong(), any());
    }
}
