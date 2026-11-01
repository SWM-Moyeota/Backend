package team.codingforest.moyeota.settlement.ledger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.settlement.ledger.domain.Payout;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutAccount;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutGateway;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutOrder;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutResult;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutStatus;
import team.codingforest.moyeota.settlement.ledger.domain.Payouts;
import team.codingforest.moyeota.settlement.ledger.domain.Settlements;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PayoutServiceUnitTest {
    private static final Long SETTLEMENT_ID = 7L;
    private static final Long NEXT_SETTLEMENT_ID = 8L;
    private static final Long PAYOUT_ID = 70L;
    private static final Long NEXT_PAYOUT_ID = 71L;
    private static final String PAYOUT_KEY = "moyeota-payout-70";
    private static final String NEXT_PAYOUT_KEY = "moyeota-payout-71";
    private static final int AMOUNT = 30000;
    private static final PayoutAccount ACCOUNT = new PayoutAccount(BankCode.KAKAO, "3333123456789", "홍길동");
    private static final PayoutOrder ORDER = new PayoutOrder(PAYOUT_ID, PAYOUT_KEY, AMOUNT, ACCOUNT);
    private static final PayoutOrder NEXT_ORDER = new PayoutOrder(NEXT_PAYOUT_ID, NEXT_PAYOUT_KEY, AMOUNT, ACCOUNT);
    private static final Instant NOW = Instant.parse("2026-10-31T01:00:00Z");
    private static final Instant STALE_BEFORE = NOW.minus(Duration.ofMinutes(10));
    private static final Instant OLD = STALE_BEFORE.minusSeconds(1);

    @Mock
    private Settlements settlements;
    @Mock
    private Payouts payouts;
    @Mock
    private PayoutGateway gateway;
    @Mock
    private PayoutRecorder recorder;

    @InjectMocks
    private PayoutService payoutService;

    // ---------- payDue ----------

    @DisplayName("payDue - 미지급 정산서가 없으면 아무것도 호출하지 않는다")
    @Test
    void payDue_noUnpaid() {

        /*
         * settings
         */
        doReturn(List.of()).when(settlements).findUnpaidIds();

        /*
         * test
         */
        payoutService.payDue();

        /*
         * verify
         */
        verifyNoInteractions(recorder, gateway, payouts);
    }

    @DisplayName("payDue - 성공 시 요청 기록, 결제사 호출, 결과 반영 순서로 진행한다")
    @Test
    void payDue_succeeded() {

        /*
         * settings
         */
        PayoutResult succeeded = new PayoutResult.Succeeded("pg-1");
        doReturn(List.of(SETTLEMENT_ID)).when(settlements).findUnpaidIds();
        doReturn(Optional.of(ORDER)).when(recorder).recordRequest(eq(SETTLEMENT_ID));
        doReturn(succeeded).when(gateway).request(eq(ORDER));

        /*
         * test
         */
        payoutService.payDue();

        /*
         * verify
         */
        InOrder order = inOrder(recorder, gateway);
        order.verify(recorder).recordRequest(eq(SETTLEMENT_ID));
        order.verify(gateway).request(eq(ORDER));
        order.verify(recorder).recordResult(eq(PAYOUT_ID), eq(succeeded));
    }

    @DisplayName("payDue - 결제사가 실패로 답하면 실패 결과를 그대로 반영한다")
    @Test
    void payDue_failedResult() {

        /*
         * settings
         */
        PayoutResult failed = new PayoutResult.Failed("예금주 불일치");
        doReturn(List.of(SETTLEMENT_ID)).when(settlements).findUnpaidIds();
        doReturn(Optional.of(ORDER)).when(recorder).recordRequest(eq(SETTLEMENT_ID));
        doReturn(failed).when(gateway).request(eq(ORDER));

        /*
         * test
         */
        payoutService.payDue();

        /*
         * verify
         */
        verify(recorder).recordResult(eq(PAYOUT_ID), eq(failed));
    }

    @DisplayName("payDue - 결제사가 처리 중으로 답하면 알 수 없음 결과를 그대로 반영한다")
    @Test
    void payDue_unknownResult() {

        /*
         * settings
         */
        PayoutResult unknown = new PayoutResult.Unknown("처리 중");
        doReturn(List.of(SETTLEMENT_ID)).when(settlements).findUnpaidIds();
        doReturn(Optional.of(ORDER)).when(recorder).recordRequest(eq(SETTLEMENT_ID));
        doReturn(unknown).when(gateway).request(eq(ORDER));

        /*
         * test
         */
        payoutService.payDue();

        /*
         * verify
         */
        verify(recorder).recordResult(eq(PAYOUT_ID), eq(unknown));
    }

    @DisplayName("payDue - 결제사가 null 을 돌려주면 응답을 못 받은 것으로 기록한다")
    @Test
    void payDue_nullResult() {

        /*
         * settings
         */
        doReturn(List.of(SETTLEMENT_ID)).when(settlements).findUnpaidIds();
        doReturn(Optional.of(ORDER)).when(recorder).recordRequest(eq(SETTLEMENT_ID));
        doReturn(null).when(gateway).request(eq(ORDER));

        /*
         * test
         */
        payoutService.payDue();

        /*
         * verify
         */
        verify(recorder).recordResult(eq(PAYOUT_ID), eq(new PayoutResult.Unknown(PayoutService.NO_RESULT_REASON)));
    }

    @DisplayName("payDue - 결제사 호출이 예외로 끝나면 실패가 아니라 알 수 없음으로 기록하고 사유에 예외 이름을 남긴다")
    @Test
    void payDue_gatewayThrows() {

        /*
         * settings
         */
        doReturn(List.of(SETTLEMENT_ID)).when(settlements).findUnpaidIds();
        doReturn(Optional.of(ORDER)).when(recorder).recordRequest(eq(SETTLEMENT_ID));
        doThrow(new IllegalStateException("타임아웃")).when(gateway).request(eq(ORDER));

        /*
         * test
         */
        payoutService.payDue();

        /*
         * verify
         */
        verify(recorder).recordResult(eq(PAYOUT_ID),
                eq(new PayoutResult.Unknown(PayoutService.NO_RESULT_REASON + ": IllegalStateException")));
    }

    @DisplayName("payDue - 결제사 호출에서 Error 가 나면 잡지 않고 결과도 반영하지 않는다")
    @Test
    void payDue_gatewayThrowsError() {

        /*
         * settings
         */
        doReturn(List.of(SETTLEMENT_ID)).when(settlements).findUnpaidIds();
        doReturn(Optional.of(ORDER)).when(recorder).recordRequest(eq(SETTLEMENT_ID));
        doThrow(new StackOverflowError()).when(gateway).request(eq(ORDER));

        /*
         * test
         */
        assertThatThrownBy(() -> payoutService.payDue())

        /*
         * verify
         */
                .isInstanceOf(StackOverflowError.class);
        verify(recorder, never()).recordResult(any(), any());
    }

    @DisplayName("payDue - 지급할 수 없는 정산서면 결제사를 부르지 않는다")
    @Test
    void payDue_nothingToRequest() {

        /*
         * settings
         */
        doReturn(List.of(SETTLEMENT_ID)).when(settlements).findUnpaidIds();
        doReturn(Optional.empty()).when(recorder).recordRequest(eq(SETTLEMENT_ID));

        /*
         * test
         */
        payoutService.payDue();

        /*
         * verify
         */
        verify(gateway, never()).request(any());
        verify(recorder, never()).recordResult(any(), any());
    }

    @DisplayName("payDue - 요청 기록이 실패한 정산서는 결제사를 부르지 않고 다음 정산서로 넘어간다")
    @Test
    void payDue_recordRequestThrows() {

        /*
         * settings
         */
        PayoutResult succeeded = new PayoutResult.Succeeded("pg-2");
        doReturn(List.of(SETTLEMENT_ID, NEXT_SETTLEMENT_ID)).when(settlements).findUnpaidIds();
        doThrow(new IllegalStateException("DB 오류")).when(recorder).recordRequest(eq(SETTLEMENT_ID));
        doReturn(Optional.of(NEXT_ORDER)).when(recorder).recordRequest(eq(NEXT_SETTLEMENT_ID));
        doReturn(succeeded).when(gateway).request(eq(NEXT_ORDER));

        /*
         * test
         */
        payoutService.payDue();

        /*
         * verify
         */
        verify(gateway, never()).request(eq(ORDER));
        verify(recorder, never()).recordResult(eq(PAYOUT_ID), any());
        verify(recorder).recordResult(eq(NEXT_PAYOUT_ID), eq(succeeded));
    }

    @DisplayName("payDue - 결제사 예외가 난 정산서가 있어도 다음 정산서를 지급한다")
    @Test
    void payDue_gatewayThrows_continues() {

        /*
         * settings
         */
        PayoutResult succeeded = new PayoutResult.Succeeded("pg-2");
        doReturn(List.of(SETTLEMENT_ID, NEXT_SETTLEMENT_ID)).when(settlements).findUnpaidIds();
        doReturn(Optional.of(ORDER)).when(recorder).recordRequest(eq(SETTLEMENT_ID));
        doReturn(Optional.of(NEXT_ORDER)).when(recorder).recordRequest(eq(NEXT_SETTLEMENT_ID));
        doThrow(new IllegalStateException("타임아웃")).when(gateway).request(eq(ORDER));
        doReturn(succeeded).when(gateway).request(eq(NEXT_ORDER));

        /*
         * test
         */
        payoutService.payDue();

        /*
         * verify
         */
        verify(recorder).recordResult(eq(PAYOUT_ID),
                eq(new PayoutResult.Unknown(PayoutService.NO_RESULT_REASON + ": IllegalStateException")));
        verify(recorder).recordResult(eq(NEXT_PAYOUT_ID), eq(succeeded));
    }

    @DisplayName("payDue - 결과 반영이 실패해도 예외를 밖으로 던지지 않고 다음 정산서를 지급한다")
    @Test
    void payDue_recordResultThrows() {

        /*
         * settings
         */
        PayoutResult first = new PayoutResult.Succeeded("pg-1");
        PayoutResult second = new PayoutResult.Succeeded("pg-2");
        doReturn(List.of(SETTLEMENT_ID, NEXT_SETTLEMENT_ID)).when(settlements).findUnpaidIds();
        doReturn(Optional.of(ORDER)).when(recorder).recordRequest(eq(SETTLEMENT_ID));
        doReturn(Optional.of(NEXT_ORDER)).when(recorder).recordRequest(eq(NEXT_SETTLEMENT_ID));
        doReturn(first).when(gateway).request(eq(ORDER));
        doReturn(second).when(gateway).request(eq(NEXT_ORDER));
        doThrow(new IllegalStateException("DB 오류")).when(recorder).recordResult(eq(PAYOUT_ID), eq(first));

        /*
         * test
         */
        payoutService.payDue();

        /*
         * verify
         */
        verify(recorder).recordResult(eq(PAYOUT_ID), eq(first));
        verify(recorder).recordResult(eq(NEXT_PAYOUT_ID), eq(second));
    }

    @DisplayName("payDue - 미지급 목록 조회가 실패하면 예외가 그대로 나가고 지급을 시작하지 않는다")
    @Test
    void payDue_findUnpaidThrows() {

        /*
         * settings
         */
        doThrow(new IllegalStateException("DB 오류")).when(settlements).findUnpaidIds();

        /*
         * test
         */
        assertThatThrownBy(() -> payoutService.payDue())

        /*
         * verify
         */
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DB 오류");
        verifyNoInteractions(recorder, gateway);
    }

    // ---------- reconcile ----------

    @DisplayName("reconcile - 현재 시각에서 10분을 뺀 시각을 기준으로 대사 대상을 찾는다")
    @Test
    void reconcile_staleCutoff() {

        /*
         * settings
         */
        doReturn(List.of()).when(payouts).findStaleIds(eq(STALE_BEFORE));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verify(payouts).findStaleIds(eq(Instant.parse("2026-10-31T00:50:00Z")));
        verifyNoInteractions(gateway, recorder);
    }

    @DisplayName("reconcile - 기준 시각이 null 이면 대상을 찾지 않고 예외가 난다")
    @Test
    void reconcile_nullNow() {

        /*
         * test
         */
        assertThatThrownBy(() -> payoutService.reconcile(null))

        /*
         * verify
         */
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(payouts, gateway, recorder);
    }

    @DisplayName("reconcile - 대상 조회가 실패하면 예외가 그대로 나간다")
    @Test
    void reconcile_findStaleThrows() {

        /*
         * settings
         */
        doThrow(new IllegalStateException("DB 오류")).when(payouts).findStaleIds(eq(STALE_BEFORE));

        /*
         * test
         */
        assertThatThrownBy(() -> payoutService.reconcile(NOW))

        /*
         * verify
         */
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DB 오류");
        verifyNoInteractions(gateway, recorder);
    }

    @DisplayName("reconcile - 그 사이 지급이 사라졌으면 결제사를 부르지 않는다")
    @Test
    void reconcile_payoutNotFound() {

        /*
         * settings
         */
        doReturn(List.of(PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.empty()).when(payouts).findById(eq(PAYOUT_ID));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verifyNoInteractions(gateway, recorder);
    }

    @DisplayName("reconcile - 그 사이 이미 끝난 지급이면 결제사를 부르지 않는다")
    @ParameterizedTest
    @EnumSource(value = PayoutStatus.class, names = {"SUCCEED", "FAILED"})
    void reconcile_alreadyFinished(PayoutStatus status) {

        /*
         * settings
         */
        doReturn(List.of(PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.of(payout(PAYOUT_ID, status, OLD))).when(payouts).findById(eq(PAYOUT_ID));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verifyNoInteractions(gateway, recorder);
    }

    @DisplayName("reconcile - 결제사에 성공 기록이 있으면 그 결과를 반영하고 다시 요청하지 않는다")
    @ParameterizedTest
    @EnumSource(value = PayoutStatus.class, names = {"REQUESTED", "UNKNOWN"})
    void reconcile_recordedSucceeded(PayoutStatus status) {

        /*
         * settings
         */
        PayoutResult succeeded = new PayoutResult.Succeeded("pg-1");
        doReturn(List.of(PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.of(payout(PAYOUT_ID, status, OLD))).when(payouts).findById(eq(PAYOUT_ID));
        doReturn(Optional.of(succeeded)).when(gateway).find(eq(PAYOUT_KEY));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verify(recorder).recordResult(eq(PAYOUT_ID), eq(succeeded));
        verify(gateway, never()).request(any());
    }

    @DisplayName("reconcile - 결제사에 실패 기록이 있으면 그 결과를 반영하고 다시 요청하지 않는다")
    @Test
    void reconcile_recordedFailed() {

        /*
         * settings
         */
        PayoutResult failed = new PayoutResult.Failed("계좌 없음");
        doReturn(List.of(PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.of(payout(PAYOUT_ID, PayoutStatus.UNKNOWN, OLD))).when(payouts).findById(eq(PAYOUT_ID));
        doReturn(Optional.of(failed)).when(gateway).find(eq(PAYOUT_KEY));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verify(recorder).recordResult(eq(PAYOUT_ID), eq(failed));
        verify(gateway, never()).request(any());
    }

    @DisplayName("reconcile - 결제사도 처리 중이면 알 수 없음을 반영하고 다시 요청하지 않는다")
    @Test
    void reconcile_recordedUnknown() {

        /*
         * settings
         */
        PayoutResult unknown = new PayoutResult.Unknown("처리 중");
        doReturn(List.of(PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.of(payout(PAYOUT_ID, PayoutStatus.REQUESTED, OLD))).when(payouts).findById(eq(PAYOUT_ID));
        doReturn(Optional.of(unknown)).when(gateway).find(eq(PAYOUT_KEY));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verify(recorder).recordResult(eq(PAYOUT_ID), eq(unknown));
        verify(gateway, never()).request(any());
    }

    @DisplayName("reconcile - 결제사에 기록이 없고 충분히 기다렸으면 같은 지급 키로 다시 요청해 결과를 반영한다")
    @ParameterizedTest
    @EnumSource(value = PayoutStatus.class, names = {"REQUESTED", "UNKNOWN"})
    void reconcile_notRecorded_retries(PayoutStatus status) {

        /*
         * settings
         */
        PayoutResult succeeded = new PayoutResult.Succeeded("pg-1");
        doReturn(List.of(PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.of(payout(PAYOUT_ID, status, OLD))).when(payouts).findById(eq(PAYOUT_ID));
        doReturn(Optional.empty()).when(gateway).find(eq(PAYOUT_KEY));
        doReturn(succeeded).when(gateway).request(eq(ORDER));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        InOrder order = inOrder(gateway, recorder);
        order.verify(gateway).find(eq(PAYOUT_KEY));
        order.verify(gateway).request(eq(ORDER));
        order.verify(recorder).recordResult(eq(PAYOUT_ID), eq(succeeded));
    }

    @DisplayName("reconcile - 생성 시각이 기준보다 1나노초 앞서면 다시 요청한다")
    @Test
    void reconcile_createdJustBeforeStale() {

        /*
         * settings
         */
        PayoutResult succeeded = new PayoutResult.Succeeded("pg-1");
        doReturn(List.of(PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.of(payout(PAYOUT_ID, PayoutStatus.UNKNOWN, STALE_BEFORE.minusNanos(1)))).when(payouts).findById(eq(PAYOUT_ID));
        doReturn(Optional.empty()).when(gateway).find(eq(PAYOUT_KEY));
        doReturn(succeeded).when(gateway).request(eq(ORDER));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verify(gateway).request(eq(ORDER));
        verify(recorder).recordResult(eq(PAYOUT_ID), eq(succeeded));
    }

    @DisplayName("reconcile - 생성 시각이 기준과 정확히 같으면 아직 기다린다")
    @Test
    void reconcile_createdExactlyAtStale() {

        /*
         * settings
         */
        doReturn(List.of(PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.of(payout(PAYOUT_ID, PayoutStatus.UNKNOWN, STALE_BEFORE))).when(payouts).findById(eq(PAYOUT_ID));
        doReturn(Optional.empty()).when(gateway).find(eq(PAYOUT_KEY));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verify(gateway, never()).request(any());
        verifyNoInteractions(recorder);
    }

    @DisplayName("reconcile - 방금 만든 알 수 없음 지급은 결제사에 기록이 없어도 다시 요청하지 않는다")
    @Test
    void reconcile_createdAfterStale() {

        /*
         * settings
         */
        doReturn(List.of(PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.of(payout(PAYOUT_ID, PayoutStatus.UNKNOWN, STALE_BEFORE.plusNanos(1)))).when(payouts).findById(eq(PAYOUT_ID));
        doReturn(Optional.empty()).when(gateway).find(eq(PAYOUT_KEY));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verify(gateway, never()).request(any());
        verifyNoInteractions(recorder);
    }

    @DisplayName("reconcile - 다시 요청한 결과가 실패면 실패를 반영한다")
    @Test
    void reconcile_retry_failed() {

        /*
         * settings
         */
        PayoutResult failed = new PayoutResult.Failed("계좌 없음");
        doReturn(List.of(PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.of(payout(PAYOUT_ID, PayoutStatus.UNKNOWN, OLD))).when(payouts).findById(eq(PAYOUT_ID));
        doReturn(Optional.empty()).when(gateway).find(eq(PAYOUT_KEY));
        doReturn(failed).when(gateway).request(eq(ORDER));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verify(recorder).recordResult(eq(PAYOUT_ID), eq(failed));
    }

    @DisplayName("reconcile - 다시 요청한 결과가 null 이면 알 수 없음으로 반영한다")
    @Test
    void reconcile_retry_nullResult() {

        /*
         * settings
         */
        doReturn(List.of(PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.of(payout(PAYOUT_ID, PayoutStatus.UNKNOWN, OLD))).when(payouts).findById(eq(PAYOUT_ID));
        doReturn(Optional.empty()).when(gateway).find(eq(PAYOUT_KEY));
        doReturn(null).when(gateway).request(eq(ORDER));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verify(recorder).recordResult(eq(PAYOUT_ID), eq(new PayoutResult.Unknown(PayoutService.NO_RESULT_REASON)));
    }

    @DisplayName("reconcile - 다시 요청이 예외로 끝나면 알 수 없음으로 반영한다")
    @Test
    void reconcile_retry_gatewayThrows() {

        /*
         * settings
         */
        doReturn(List.of(PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.of(payout(PAYOUT_ID, PayoutStatus.UNKNOWN, OLD))).when(payouts).findById(eq(PAYOUT_ID));
        doReturn(Optional.empty()).when(gateway).find(eq(PAYOUT_KEY));
        doThrow(new IllegalArgumentException("잘못된 요청")).when(gateway).request(eq(ORDER));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verify(recorder).recordResult(eq(PAYOUT_ID),
                eq(new PayoutResult.Unknown(PayoutService.NO_RESULT_REASON + ": IllegalArgumentException")));
    }

    @DisplayName("reconcile - 지급 조회가 실패한 건은 건너뛰고 다음 지급을 대사한다")
    @Test
    void reconcile_findByIdThrows_continues() {

        /*
         * settings
         */
        PayoutResult succeeded = new PayoutResult.Succeeded("pg-2");
        doReturn(List.of(PAYOUT_ID, NEXT_PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doThrow(new IllegalStateException("DB 오류")).when(payouts).findById(eq(PAYOUT_ID));
        doReturn(Optional.of(payout(NEXT_PAYOUT_ID, PayoutStatus.UNKNOWN, OLD))).when(payouts).findById(eq(NEXT_PAYOUT_ID));
        doReturn(Optional.of(succeeded)).when(gateway).find(eq(NEXT_PAYOUT_KEY));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verify(gateway, never()).find(eq(PAYOUT_KEY));
        verify(recorder).recordResult(eq(NEXT_PAYOUT_ID), eq(succeeded));
    }

    @DisplayName("reconcile - 결제사 조회가 실패하면 그 건은 다시 요청하지 않고 다음 지급을 대사한다")
    @Test
    void reconcile_findThrows_continues() {

        /*
         * settings
         */
        PayoutResult succeeded = new PayoutResult.Succeeded("pg-2");
        doReturn(List.of(PAYOUT_ID, NEXT_PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.of(payout(PAYOUT_ID, PayoutStatus.UNKNOWN, OLD))).when(payouts).findById(eq(PAYOUT_ID));
        doReturn(Optional.of(payout(NEXT_PAYOUT_ID, PayoutStatus.UNKNOWN, OLD))).when(payouts).findById(eq(NEXT_PAYOUT_ID));
        doThrow(new IllegalStateException("타임아웃")).when(gateway).find(eq(PAYOUT_KEY));
        doReturn(Optional.of(succeeded)).when(gateway).find(eq(NEXT_PAYOUT_KEY));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verify(gateway, never()).request(any());
        verify(recorder, never()).recordResult(eq(PAYOUT_ID), any());
        verify(recorder).recordResult(eq(NEXT_PAYOUT_ID), eq(succeeded));
    }

    @DisplayName("reconcile - 결제사 기록 반영이 실패해도 다음 지급을 대사한다")
    @Test
    void reconcile_recordResultThrows_continues() {

        /*
         * settings
         */
        PayoutResult first = new PayoutResult.Succeeded("pg-1");
        PayoutResult second = new PayoutResult.Succeeded("pg-2");
        doReturn(List.of(PAYOUT_ID, NEXT_PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.of(payout(PAYOUT_ID, PayoutStatus.UNKNOWN, OLD))).when(payouts).findById(eq(PAYOUT_ID));
        doReturn(Optional.of(payout(NEXT_PAYOUT_ID, PayoutStatus.UNKNOWN, OLD))).when(payouts).findById(eq(NEXT_PAYOUT_ID));
        doReturn(Optional.of(first)).when(gateway).find(eq(PAYOUT_KEY));
        doReturn(Optional.of(second)).when(gateway).find(eq(NEXT_PAYOUT_KEY));
        doThrow(new IllegalStateException("DB 오류")).when(recorder).recordResult(eq(PAYOUT_ID), eq(first));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verify(recorder).recordResult(eq(PAYOUT_ID), eq(first));
        verify(recorder).recordResult(eq(NEXT_PAYOUT_ID), eq(second));
    }

    @DisplayName("reconcile - 다시 요청한 결과의 반영이 실패해도 다음 지급을 대사한다")
    @Test
    void reconcile_retry_recordResultThrows_continues() {

        /*
         * settings
         */
        PayoutResult first = new PayoutResult.Succeeded("pg-1");
        PayoutResult second = new PayoutResult.Succeeded("pg-2");
        doReturn(List.of(PAYOUT_ID, NEXT_PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.of(payout(PAYOUT_ID, PayoutStatus.UNKNOWN, OLD))).when(payouts).findById(eq(PAYOUT_ID));
        doReturn(Optional.of(payout(NEXT_PAYOUT_ID, PayoutStatus.UNKNOWN, OLD))).when(payouts).findById(eq(NEXT_PAYOUT_ID));
        doReturn(Optional.empty()).when(gateway).find(eq(PAYOUT_KEY));
        doReturn(first).when(gateway).request(eq(ORDER));
        doThrow(new IllegalStateException("DB 오류")).when(recorder).recordResult(eq(PAYOUT_ID), eq(first));
        doReturn(Optional.of(second)).when(gateway).find(eq(NEXT_PAYOUT_KEY));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verify(recorder).recordResult(eq(PAYOUT_ID), eq(first));
        verify(recorder).recordResult(eq(NEXT_PAYOUT_ID), eq(second));
    }

    @DisplayName("reconcile - 생성 시각이 비어 대기 판단이 불가능한 지급은 다시 요청하지 않고 다음 지급을 대사한다")
    @Test
    void reconcile_nullCreatedAt_continues() {

        /*
         * settings
         */
        PayoutResult succeeded = new PayoutResult.Succeeded("pg-2");
        doReturn(List.of(PAYOUT_ID, NEXT_PAYOUT_ID)).when(payouts).findStaleIds(eq(STALE_BEFORE));
        doReturn(Optional.of(payout(PAYOUT_ID, PayoutStatus.UNKNOWN, null))).when(payouts).findById(eq(PAYOUT_ID));
        doReturn(Optional.of(payout(NEXT_PAYOUT_ID, PayoutStatus.UNKNOWN, OLD))).when(payouts).findById(eq(NEXT_PAYOUT_ID));
        doReturn(Optional.empty()).when(gateway).find(eq(PAYOUT_KEY));
        doReturn(Optional.of(succeeded)).when(gateway).find(eq(NEXT_PAYOUT_KEY));

        /*
         * test
         */
        payoutService.reconcile(NOW);

        /*
         * verify
         */
        verify(gateway, never()).request(any());
        verify(recorder, never()).recordResult(eq(PAYOUT_ID), any());
        verify(recorder).recordResult(eq(NEXT_PAYOUT_ID), eq(succeeded));
    }

    private static Payout payout(Long id, PayoutStatus status, Instant createdAt) {
        return Payout.load(id, SETTLEMENT_ID, AMOUNT, status, null, ACCOUNT.bankCode(), ACCOUNT.accountNumber(),
                ACCOUNT.accountHolder(), null, createdAt, createdAt);
    }
}
