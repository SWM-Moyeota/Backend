package team.codingforest.moyeota.faresplit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.faresplit.domain.enums.Bank;
import team.codingforest.moyeota.faresplit.domain.FareSplit;
import team.codingforest.moyeota.faresplit.domain.FareSplitNotifier;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitStatus;
import team.codingforest.moyeota.faresplit.domain.FareSplitTransfer;
import team.codingforest.moyeota.faresplit.domain.FareSplits;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FareSplitNotificationServiceUnitTest {
    private static final Long PARTY_ID = 100L;
    private static final Long PAYER_ID = 1L;
    private static final Long SENDER_ID = 2L;
    private static final Long OTHER_SENDER_ID = 3L;
    // 정산을 시작하며 첫 송금 요청을 보낸 시각
    private static final Instant CREATED_AT = Instant.parse("2026-11-03T03:00:00Z");
    private static final Instant SENT_AT = CREATED_AT.plus(Duration.ofMinutes(5));
    private static final AccountNumber ACCOUNT = new AccountNumber("3333012345678");
    private static final String REMINDER_TASK = "fare-split.transfer-reminder";
    private static final String CONFIRM_TASK = "fare-split.confirm-request";

    @Mock
    private FareSplits fareSplits;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;
    @Mock
    private FareSplitNotifier notifier;

    @InjectMocks
    private FareSplitNotificationService service;

    // ───────────────────────── remindTransfers ─────────────────────────

    @DisplayName("remindTransfers - 정산 행을 잠근 뒤 재요청 시각을 남겨 저장하고, 커밋되면 아직 안 보낸 사람에게 1인당 금액으로 재요청을 보낸다")
    @Test
    void remindTransfers() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitTransfer.load(SENDER_ID, SENT_AT, null, CREATED_AT, null),
                FareSplitTransfer.load(OTHER_SENDER_ID, null, null, CREATED_AT, null));
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        Instant now = CREATED_AT.plus(Duration.ofHours(4));

        /*
         * test
         */
        service.remindTransfers(PARTY_ID, now);

        /*
         * verify
         */
        InOrder order = inOrder(fareSplits, afterCommitExecutor);
        order.verify(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        order.verify(fareSplits).save(eq(split));
        order.verify(afterCommitExecutor).execute(eq(REMINDER_TASK), any());
        assertThat(split.findTransfer(OTHER_SENDER_ID).orElseThrow().getLastRequestedAt()).isEqualTo(now);

        Runnable task = capturedTask(REMINDER_TASK);
        verifyNoInteractions(notifier);
        task.run();
        verify(notifier).remindTransfer(eq(PARTY_ID), eq(List.of(OTHER_SENDER_ID)), eq(6_240));
    }

    @DisplayName("remindTransfers - 잠가 보니 차례가 아니면(다른 서버가 먼저 처리함) 저장하지도 보내지도 않는다")
    @Test
    void remindTransfers_notDue() {

        /*
         * settings
         */
        // 다른 서버가 방금 재요청을 보냈다
        Instant now = CREATED_AT.plus(Duration.ofHours(4));
        FareSplit split = split(FareSplitTransfer.load(SENDER_ID, null, null, now, null));
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.remindTransfers(PARTY_ID, now);

        /*
         * verify
         */
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, notifier);
    }

    @DisplayName("remindTransfers - 조회와 잠금 사이에 취소된 정산이면 예외 없이 건너뛴다")
    @Test
    void remindTransfers_canceled() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.remindTransfers(PARTY_ID, CREATED_AT.plus(Duration.ofHours(1))));

        /*
         * verify
         */
        assertThat(thrown).isNull();
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor);
    }

    // ───────────────────────── requestConfirm ─────────────────────────

    @DisplayName("requestConfirm - 정산 행을 잠근 뒤 요청 시각을 남겨 저장하고, 커밋되면 받는 사람에게 입금 확인을 요청한다")
    @Test
    void requestConfirm() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitTransfer.load(SENDER_ID, SENT_AT, null, CREATED_AT, null));
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        Instant now = SENT_AT.plus(Duration.ofHours(4));

        /*
         * test
         */
        service.requestConfirm(PARTY_ID, now);

        /*
         * verify
         */
        InOrder order = inOrder(fareSplits, afterCommitExecutor);
        order.verify(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        order.verify(fareSplits).save(eq(split));
        order.verify(afterCommitExecutor).execute(eq(CONFIRM_TASK), any());
        assertThat(split.findTransfer(SENDER_ID).orElseThrow().getConfirmRequestedAt()).isEqualTo(now);

        Runnable task = capturedTask(CONFIRM_TASK);
        verifyNoInteractions(notifier);
        task.run();
        verify(notifier).requestConfirm(eq(PARTY_ID), eq(PAYER_ID));
    }

    @DisplayName("requestConfirm - 잠가 보니 이미 요청했거나 확인됐으면 저장하지도 보내지도 않는다")
    @Test
    void requestConfirm_notDue() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitTransfer.load(SENDER_ID, SENT_AT, null, CREATED_AT, SENT_AT.plus(Duration.ofHours(4))),
                FareSplitTransfer.load(OTHER_SENDER_ID, SENT_AT, SENT_AT.plus(Duration.ofHours(1)), CREATED_AT, null));
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.requestConfirm(PARTY_ID, SENT_AT.plus(Duration.ofHours(5)));

        /*
         * verify
         */
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, notifier);
    }

    @DisplayName("requestConfirm - 조회와 잠금 사이에 취소된 정산이면 예외 없이 건너뛴다")
    @Test
    void requestConfirm_canceled() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.requestConfirm(PARTY_ID, SENT_AT.plus(Duration.ofHours(4))));

        /*
         * verify
         */
        assertThat(thrown).isNull();
        verifyNoInteractions(afterCommitExecutor);
    }

    // ───────────────────────── fixtures ─────────────────────────

    /** 택시비 18,700원, 1인당 6,240원인 OPEN 정산 */
    private static FareSplit split(FareSplitTransfer... transfers) {
        return FareSplit.load(PARTY_ID, PAYER_ID, 18_700, 6_240, Bank.KAKAO, ACCOUNT, FareSplitStatus.OPEN, CREATED_AT, null, List.of(transfers));
    }

    private Runnable capturedTask(String name) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommitExecutor).execute(eq(name), captor.capture());

        return captor.getValue();
    }
}
