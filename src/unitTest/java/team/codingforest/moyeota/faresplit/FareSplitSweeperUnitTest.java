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

@ExtendWith(MockitoExtension.class)
class FareSplitSweeperUnitTest {
    // 한국 시각 11-04 12:00 - 방해 금지 시간이 아니다
    private static final Instant NOON = Instant.parse("2026-11-04T03:00:00Z");
    // 한국 시각 11-04 23:30 - 방해 금지 시간
    private static final Instant QUIET = Instant.parse("2026-11-04T14:30:00Z");

    @Mock
    private FareSplits fareSplits;
    @Mock
    private FareSplitService fareSplitService;
    @Mock
    private FareSplitNotificationService notificationService;

    @InjectMocks
    private FareSplitSweeper sweeper;

    @DisplayName("sweepAt - 한 번 돌 때의 시각 하나로 대상을 찾아 만료 → 송금 재요청 → 입금 확인 요청 순으로 정산마다 서비스에 넘긴다")
    @Test
    void sweepAt() {

        /*
         * settings
         */
        doReturn(List.of(1L)).when(fareSplits).findExpireTargets(any());
        doReturn(List.of(2L, 3L)).when(fareSplits).findTransferRequestTargets(any());
        doReturn(List.of(4L)).when(fareSplits).findConfirmRequestTargets(any());

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
        order.verify(fareSplits).findConfirmRequestTargets(eq(NOON));
        order.verify(notificationService).requestConfirm(eq(4L), eq(NOON));
    }

    @DisplayName("sweepAt - 한 정산이 실패해도(업무 예외·DB 예외·그 밖의 런타임 예외) 나머지 정산과 다음 작업을 계속한다")
    @Test
    void sweepAt_continuesAfterFailure() {

        /*
         * settings
         */
        doReturn(List.of(1L, 2L)).when(fareSplits).findExpireTargets(any());
        doReturn(List.of(3L, 4L)).when(fareSplits).findTransferRequestTargets(any());
        doReturn(List.of(5L, 6L)).when(fareSplits).findConfirmRequestTargets(any());
        doThrow(new PessimisticLockingFailureException("잠금 대기 시간 초과")).when(fareSplitService).expire(eq(1L), any());
        doThrow(new BusinessException(FareSplitErrorCode.FARE_SPLIT_NOT_FOUND)).when(notificationService).remindTransfers(eq(3L), any());
        doThrow(new IllegalStateException("알 수 없는 실패")).when(notificationService).requestConfirm(eq(5L), any());

        /*
         * test
         */
        sweeper.sweepAt(NOON);

        /*
         * verify
         */
        verify(fareSplitService).expire(eq(2L), eq(NOON));
        verify(notificationService).remindTransfers(eq(4L), eq(NOON));
        verify(notificationService).requestConfirm(eq(6L), eq(NOON));
    }

    @DisplayName("sweepAt - 방해 금지 시간에는 만료와 송금 재요청(차례만 넘김)은 하고 입금 확인 요청 대상은 찾지 않는다")
    @Test
    void sweepAt_quietHours() {

        /*
         * settings
         */
        doReturn(List.of(1L)).when(fareSplits).findExpireTargets(any());
        doReturn(List.of(2L)).when(fareSplits).findTransferRequestTargets(any());

        /*
         * test
         */
        sweeper.sweepAt(QUIET);

        /*
         * verify
         */
        verify(fareSplitService).expire(eq(1L), eq(QUIET));
        verify(notificationService).remindTransfers(eq(2L), eq(QUIET));
        verify(fareSplits, never()).findConfirmRequestTargets(any());
        verify(notificationService, never()).requestConfirm(anyLong(), any());
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
