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
            reconcileSafely(payoutId, now, staleBefore);
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

    private void reconcileSafely(Long payoutId, Instant now, Instant staleBefore) {
        try {
            reconcileOne(payoutId, now, staleBefore);
        } catch (RuntimeException e) {
            log.error("지급 대사 실패, 다음 대사에서 다시 확인 payoutId={}", payoutId, e);
        }
    }

    /**
     * 결제사가 접수한 지급은 조회로 결과를 확인하고, 응답을 못 받은 지급은 같은 지급 키로 다시 보내 처음 결과를 받음
     *
     * @param payoutId    지급 id
     * @param now         지금 시각
     * @param staleBefore 이보다 먼저 만든 요청만 멈춘 것으로 봄
     */
    private void reconcileOne(Long payoutId, Instant now, Instant staleBefore) {
        Optional<Payout> found = payouts.findById(payoutId);
        if(found.isEmpty() || found.get().isFinished()) return;

        Payout payout = found.get();
        if(payout.hasPgId()) {
            PayoutResult result = findAtGateway(payout);
            if(result instanceof PayoutResult.Accepted) return;

            recorder.recordResult(payoutId, result);
            return;
        }

        boolean waitedLongEnough = payout.getCreatedAt().isBefore(staleBefore);
        if(!waitedLongEnough) return;

        if(!payout.canResend(now)) {
            log.error("멱등키 유효 기간이 지나 다시 요청하지 않음, 결제사에서 직접 확인 필요 payoutId={}, payoutKey={}", payoutId, payout.payoutKey());
            return;
        }

        PayoutResult result = callGateway(payout.toOrder());
        recorder.recordResult(payoutId, result);
        log.info("응답을 받지 못한 지급을 같은 지급 키로 다시 요청 payoutId={}, result={}", payoutId, result);
    }

    private PayoutResult findAtGateway(Payout payout) {
        try {
            return gateway.find(payout.getPgId());
        } catch (RuntimeException e) {
            log.warn("결제사 지급 조회 실패, 다음 대사에서 다시 확인 payoutId={}, pgId={}", payout.getId(), payout.getPgId(), e);
            return new PayoutResult.Unknown(NO_RESULT_REASON + ": " + e.getClass().getSimpleName());
        }
    }
}
