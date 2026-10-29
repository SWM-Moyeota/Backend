package team.codingforest.moyeota.settlement.ledger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.settlement.ledger.domain.Payout;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutGateway;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutOrder;
import team.codingforest.moyeota.settlement.ledger.domain.PayoutResult;
import team.codingforest.moyeota.settlement.ledger.domain.Payouts;
import team.codingforest.moyeota.settlement.ledger.domain.Settlements;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@ConditionalOnProperty(prefix = "moyeota.settlement.payout", name = "enabled", havingValue = "true")
@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutService {
    static final Duration STALE_AFTER = Duration.ofMinutes(10);
    static final String NO_RESULT_REASON = "지급 요청 결과를 받지 못함";

    private final Settlements settlements;
    private final Payouts payouts;
    private final PayoutGateway gateway;
    private final PayoutRecorder recorder;

    public void payDue() {
        for(Long settlementId : settlements.findUnpaidIds()) {
            payOne(settlementId);
        }
    }

    public void reconcile(Instant now) {
        Instant staleBefore = now.minus(STALE_AFTER);

        for(Long payoutId : payouts.findIdsToReconcile(staleBefore)) {
            reconcileSafely(payoutId, staleBefore);
        }
    }

    private void payOne(Long settlementId) {
        Optional<PayoutOrder> order = recordRequestSafely(settlementId);
        if(order.isEmpty()) return;

        PayoutResult result = callGateway(order.get());
        recordResultSafely(order.get(), result);
    }

    private Optional<PayoutOrder> recordRequestSafely(Long settlementId) {
        try {
            return recorder.recordRequest(settlementId);
        } catch (RuntimeException e) {
            log.error("지급 요청 기록 실패 settlementId={}", settlementId, e);
            return Optional.empty();
        }
    }

    private PayoutResult callGateway(PayoutOrder order) {
        try {
            PayoutResult result = gateway.request(order);

            if(result == null) return new PayoutResult.Unknown(NO_RESULT_REASON);

            return result;
        } catch (RuntimeException e) {
            log.warn("결제사 지급 요청 응답을 받지 못함 payoutId={}", order.payoutId(), e);
            return new PayoutResult.Unknown(NO_RESULT_REASON + ": " + e.getClass().getSimpleName());
        }
    }

    private void recordResultSafely(PayoutOrder order, PayoutResult result) {
        try {
            recorder.recordResult(order.payoutId(), result);
        } catch (RuntimeException e) {
            log.error("지급 결과 반영 실패, 대사에서 확정 payoutId={}, result={}", order.payoutId(), result, e);
        }
    }

    private void reconcileSafely(Long payoutId, Instant staleBefore) {
        try {
            reconcileOne(payoutId, staleBefore);
        } catch (RuntimeException e) {
            log.error("지급 대사 실패, 다음 대사에서 다시 확인 payoutId={}", payoutId, e);
        }
    }

    private void reconcileOne(Long payoutId, Instant staleBefore) {
        Optional<Payout> found = payouts.findById(payoutId);
        if(found.isEmpty() || found.get().isFinished()) return;

        Payout payout = found.get();
        Optional<PayoutResult> recorded = gateway.find(payout.payoutKey());
        if(recorded.isPresent()) {
            recorder.recordResult(payoutId, recorded.get());
            return;
        }

        boolean waitedLongEnough = payout.getCreatedAt().isBefore(staleBefore);
        if(!waitedLongEnough) return;

        PayoutResult result = callGateway(payout.toOrder());
        recorder.recordResult(payoutId, result);
        log.info("결제사에 기록이 없는 지급을 같은 지급 키로 다시 요청 payoutId={}, result={}", payoutId, result);
    }
}
