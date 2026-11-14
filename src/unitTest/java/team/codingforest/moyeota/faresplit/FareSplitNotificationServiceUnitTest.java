package team.codingforest.moyeota.faresplit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.common.transaction.AfterCommitExecutor;
import team.codingforest.moyeota.faresplit.domain.AccountNumber;
import team.codingforest.moyeota.faresplit.domain.enums.Bank;
import team.codingforest.moyeota.faresplit.domain.FarePayment;
import team.codingforest.moyeota.faresplit.domain.FareSplit;
import team.codingforest.moyeota.faresplit.domain.FareSplitNotifier;
import team.codingforest.moyeota.faresplit.domain.enums.FareSplitStatus;
import team.codingforest.moyeota.faresplit.domain.FareSplitTransfer;
import team.codingforest.moyeota.faresplit.domain.FareSplits;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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

    @Mock
    private FareSplits fareSplits;
    @Mock
    private AfterCommitExecutor afterCommitExecutor;
    @Mock
    private FareSplitNotifier notifier;

    @InjectMocks
    private FareSplitNotificationService service;

    // ───────────────────────── remindTransfers ─────────────────────────

    @DisplayName("remindTransfers - 정산 행을 잠근 뒤 차례가 된 사람의 요청 횟수를 올려 저장하고, 커밋되면 아직 안 보낸 사람에게만 그 사람이 보낼 금액으로 재요청을 보낸다. 받는 사람(방장)의 줄은 대상이 아니다")
    @Test
    void remindTransfers() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitTransfer.load(SENDER_ID, 6_240, SENT_AT, null, 1),
                FareSplitTransfer.load(OTHER_SENDER_ID, 6_240, null, null, 1));
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));
        Instant now = CREATED_AT.plus(FareSplit.REMIND_INTERVAL);

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
        assertThat(split.findTransfer(OTHER_SENDER_ID).orElseThrow().getRequestCount()).isEqualTo(2);
        assertThat(split.findTransfer(SENDER_ID).orElseThrow().getRequestCount()).as("이미 보낸 사람은 세지 않는다").isEqualTo(1);
        assertThat(split.findTransfer(PAYER_ID).orElseThrow().getRequestCount()).as("받는 사람의 줄은 요청을 세지 않는다").isZero();

        Runnable task = capturedTask(REMINDER_TASK);
        verifyNoInteractions(notifier);
        task.run();
        verify(notifier).remindTransfer(eq(PARTY_ID), eq(List.of(new FarePayment(OTHER_SENDER_ID, 6_240))));
        verifyNoMoreInteractions(notifier);
    }

    @DisplayName("remindTransfers - 사람마다 보낼 금액이 다르면 재요청에 받는 사람(방장)을 뺀 사람별 금액 목록이 송금 순서대로 실린다")
    @Test
    void remindTransfers_differentAmounts() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitTransfer.load(SENDER_ID, 7_000, null, null, 1),
                FareSplitTransfer.load(OTHER_SENDER_ID, 5_000, null, null, 1));
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.remindTransfers(PARTY_ID, CREATED_AT.plus(FareSplit.REMIND_INTERVAL));

        /*
         * verify
         */
        verify(fareSplits).save(eq(split));
        capturedTask(REMINDER_TASK).run();

        ArgumentCaptor<List<FarePayment>> captor = ArgumentCaptor.captor();
        verify(notifier).remindTransfer(eq(PARTY_ID), captor.capture());
        assertThat(captor.getValue()).containsExactly(new FarePayment(SENDER_ID, 7_000), new FarePayment(OTHER_SENDER_ID, 5_000));
    }

    @DisplayName("remindTransfers - 잠가 보니 차례가 아니면(다른 서버가 먼저 처리함) 저장하지도 보내지도 않는다")
    @Test
    void remindTransfers_notDue() {

        /*
         * settings
         */
        // 다른 서버가 방금 4시간째 재요청을 보내 요청 횟수가 2가 됐다
        Instant now = CREATED_AT.plus(FareSplit.REMIND_INTERVAL);
        FareSplit split = split(FareSplitTransfer.load(SENDER_ID, 6_240, null, null, 2));
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.remindTransfers(PARTY_ID, now);

        /*
         * verify
         */
        assertThat(split.findTransfer(SENDER_ID).orElseThrow().getRequestCount()).isEqualTo(2);
        assertThat(split.findTransfer(PAYER_ID).orElseThrow().getRequestCount()).isZero();
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, notifier);
    }

    @DisplayName("remindTransfers - 보내는 사람이 모두 보냈으면 받는 사람(방장)의 줄만 남아도 저장하지도 보내지도 않는다")
    @Test
    void remindTransfers_onlyPayerLeft() {

        /*
         * settings
         */
        FareSplit split = split(FareSplitTransfer.load(SENDER_ID, 6_240, SENT_AT, null, 1),
                FareSplitTransfer.load(OTHER_SENDER_ID, 6_240, SENT_AT, SENT_AT.plusSeconds(60), 1));
        doReturn(Optional.of(split)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.remindTransfers(PARTY_ID, CREATED_AT.plus(FareSplit.REMIND_INTERVAL));

        /*
         * verify
         */
        assertThat(split.findTransfer(PAYER_ID).orElseThrow().getRequestCount()).isZero();
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, notifier);
    }

    @DisplayName("remindTransfers - 조회와 잠금 사이에 닫힌 정산이면 저장하지도 보내지도 않는다")
    @Test
    void remindTransfers_closed() {

        /*
         * settings
         */
        Instant closedAt = CREATED_AT.plus(Duration.ofHours(3));
        FareSplit completed = FareSplit.load(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, null, FareSplitStatus.COMPLETED, CREATED_AT, closedAt,
                List.of(payerTransfer(12_460),
                        FareSplitTransfer.load(SENDER_ID, 6_240, SENT_AT, closedAt, 1)));
        doReturn(Optional.of(completed)).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        service.remindTransfers(PARTY_ID, CREATED_AT.plus(FareSplit.REMIND_INTERVAL));

        /*
         * verify
         */
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, notifier);
    }

    @DisplayName("remindTransfers - 정산이 없으면 건너뛰지 않고 FARE_SPLIT_NOT_FOUND")
    @Test
    void remindTransfers_notFound() {

        /*
         * settings
         */
        doReturn(Optional.empty()).when(fareSplits).findByPartyIdForUpdate(eq(PARTY_ID));

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> service.remindTransfers(PARTY_ID, CREATED_AT.plus(FareSplit.REMIND_INTERVAL)));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(FareSplitErrorCode.FARE_SPLIT_NOT_FOUND);
        verify(fareSplits, never()).save(any());
        verifyNoInteractions(afterCommitExecutor, notifier);
    }

    // ───────────────────────── fixtures ─────────────────────────

    /** 택시비 18,700원인 OPEN 정산. 보내는 사람의 줄만 넘기면 받는 사람(방장)의 줄을 남은 금액으로 맨 앞에 넣는다 */
    private static FareSplit split(FareSplitTransfer... senderTransfers) {
        int sendersTotal = Stream.of(senderTransfers).mapToInt(FareSplitTransfer::getPayment).sum();
        List<FareSplitTransfer> transfers = Stream.concat(Stream.of(payerTransfer(18_700 - sendersTotal)), Stream.of(senderTransfers)).toList();

        return FareSplit.load(PARTY_ID, PAYER_ID, 18_700, Bank.KAKAO, ACCOUNT, FareSplitStatus.OPEN, CREATED_AT, null, transfers);
    }

    /** 받는 사람(방장)의 줄 - 정산을 시작할 때 CONFIRMED, 요청 횟수 0 으로 생긴다 */
    private static FareSplitTransfer payerTransfer(int payment) {
        return FareSplitTransfer.load(PAYER_ID, payment, CREATED_AT, CREATED_AT, 0);
    }

    private Runnable capturedTask(String name) {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(afterCommitExecutor).execute(eq(name), captor.capture());

        return captor.getValue();
    }
}
