package team.codingforest.moyeota.payment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.domain.ChargeOrder;
import team.codingforest.moyeota.payment.domain.ChargeResult;
import team.codingforest.moyeota.payment.domain.GatewayPayment;
import team.codingforest.moyeota.payment.domain.Payment;
import team.codingforest.moyeota.payment.domain.PaymentGroup;
import team.codingforest.moyeota.payment.domain.PaymentGroups;
import team.codingforest.moyeota.payment.domain.PaymentMethod;
import team.codingforest.moyeota.payment.domain.PaymentMethods;
import team.codingforest.moyeota.payment.domain.PaymentStatus;
import team.codingforest.moyeota.payment.domain.PaymentType;
import team.codingforest.moyeota.payment.domain.PgPaymentId;
import team.codingforest.moyeota.payment.dto.PaymentResponse;
import team.codingforest.moyeota.payment.dto.RideCompletedCommand;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 *  결제 그룹에 대한 DB 작업을 한 단계씩 독립 트랜잭션(REQUIRES_NEW)으로 나눈 것.
 *  결제사 HTTP 호출 동안 트랜잭션·행 잠금을 들고 있지 않기 위해 서비스는 "단계 → 결제사 호출 → 단계" 로 조립한다.
 *  (서비스 안에서 @Transactional 메서드를 자기 호출하면 프록시를 타지 않아 트랜잭션이 안 걸린다 - 그래서 별도 빈)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentChargeSteps {
    public static final String FAIL_NO_PAYMENT_METHOD = "NO_PAYMENT_METHOD";
    public static final String FAIL_GATEWAY_UNREACHABLE = "GATEWAY_UNREACHABLE";

    private final PaymentGroups paymentGroups;
    private final PaymentMethods paymentMethods;
    private final PaymentPolicy policy;

    /** 그룹 생성 - 이미 있으면 그 id. 이벤트 재전송에 대한 멱등 보장 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long open(RideCompletedCommand command) {
        Optional<PaymentGroup> existing = paymentGroups.findByPartyId(command.partyId());
        if(existing.isPresent()) {
            log.info("이미 결제 그룹이 있는 방 - 남은 청구만 처리 partyId={}, groupId={}", command.partyId(), existing.get().getId());
            return existing.get().getId();
        }

        String orderName = "모여타 택시 동승 (" + command.departure() + " → " + command.destination() + ")";
        PaymentGroup group = PaymentGroup.open(command.partyId(), command.driverId(), command.fare(), command.memberIds(), policy.getPlatformCharge(), orderName, Instant.now());
        Long groupId = paymentGroups.save(group).getId();

        log.info("결제 그룹 생성 partyId={}, groupId={}, fare={}, passengers={}", command.partyId(), groupId, command.fare(), command.memberIds().size());
        return groupId;
    }

    /**
     *  청구 준비 - 유저의 활성 결제수단을 붙이고 결제사 식별자를 발급한다.
     *  결제수단이 없으면 그 자리에서 FAILED(NO_PAYMENT_METHOD) 로 기록하고 empty.
     *  READY 가 아니거나 이미 식별자가 있는(응답 유실) 건은 다시 보내지 않고 empty.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<ChargeOrder> prepare(Long groupId, Long paymentId) {
        PaymentGroup group = getForUpdate(groupId);
        Payment payment = group.getPayment(paymentId);

        if(!payment.isReady() || payment.getPgPaymentId() != null) return Optional.empty();

        Optional<PaymentMethod> method = paymentMethods.findActiveByUserId(payment.getUserId());

        if(method.isEmpty()) {
            group.markFailed(paymentId, FAIL_NO_PAYMENT_METHOD, Instant.now());
            paymentGroups.save(group);

            log.info("결제수단 없음 - 청구 실패 기록 groupId={}, paymentId={}, userId={}", groupId, paymentId, payment.getUserId());
            return Optional.empty();
        }

        String pgPaymentId = PgPaymentId.next();
        group.attempt(paymentId, method.get().getId(), pgPaymentId);
        paymentGroups.save(group);

        return Optional.of(new ChargeOrder(pgPaymentId, method.get().getBillingKey(), payment.getAmount(), payment.getCurrency(), payment.getOrderName(), payment.getUserId(), null));
    }

    /** 결제사 응답 반영. 웹훅이 먼저 와서 이미 반영됐으면 건너뛴다 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long groupId, Long paymentId, ChargeResult result) {
        PaymentGroup group = getForUpdate(groupId);
        Instant now = Instant.now();

        if(!group.getPayment(paymentId).isReady()) {
            log.info("이미 반영된 결제 - 결과 기록 생략 groupId={}, paymentId={}", groupId, paymentId);
            return;
        }

        if(result.paid()) {
            group.markPaid(paymentId, result.paidAt() != null ? result.paidAt() : now);
        }
        else {
            group.markFailed(paymentId, result.failCode(), now);
        }

        paymentGroups.save(group);
    }

    /**
     *  결제사 기준 상태를 우리 기록에 맞춘다. 웹훅·응답 유실 복구 공용.
     *  결제사에 없는 결제(empty)는 청구가 닿지 않은 것 - 실패로 기록해 사용자가 다시 시도할 수 있게 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sync(Long groupId, String pgPaymentId, Optional<GatewayPayment> remote) {
        PaymentGroup group = getForUpdate(groupId);
        Payment payment = group.getPaymentByPgPaymentId(pgPaymentId);
        Instant now = Instant.now();

        if(remote.isEmpty()) {
            if(payment.isReady()) group.markFailed(payment.getId(), FAIL_GATEWAY_UNREACHABLE, now);
            paymentGroups.save(group);
            return;
        }

        GatewayPayment gateway = remote.get();

        switch(gateway.status()) {
            case PAID -> {
                if(payment.isReady()) group.markPaid(payment.getId(), gateway.paidAt() != null ? gateway.paidAt() : now);
            }
            case FAILED -> {
                if(payment.isReady()) group.markFailed(payment.getId(), gateway.failCode() != null ? gateway.failCode() : "FAILED", now);
            }
            case CANCELED -> {
                // 취소는 결제 이후에만 생기므로, 아직 READY 면 결제 완료를 먼저 반영한 뒤 취소한다
                if(payment.isReady()) group.markPaid(payment.getId(), gateway.paidAt() != null ? gateway.paidAt() : now);
                if(payment.getStatus() == PaymentStatus.PAID) {
                    group.cancel(payment.getId(), gateway.cancelReason(), gateway.canceledAt() != null ? gateway.canceledAt() : now);
                }
            }
            case PENDING -> log.info("결제사 처리 중 - 대기 유지 groupId={}, pgPaymentId={}", groupId, pgPaymentId);
        }

        paymentGroups.save(group);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetry(Long groupId, Long paymentId, Long requesterId) {
        PaymentGroup group = getForUpdate(groupId);

        group.retry(paymentId, requesterId, Instant.now());
        paymentGroups.save(group);
    }

    /** 대납 건 생성 → 새 결제 id */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long openCover(Long groupId, Long payerId) {
        PaymentGroup group = getForUpdate(groupId);

        group.cover(payerId, Instant.now());
        PaymentGroup saved = paymentGroups.save(group);

        return saved.getPayments().stream()
                .filter(p -> p.getType() == PaymentType.COVER && p.isReady())
                .map(Payment::getId)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Long> findReadyPaymentIds(Long groupId) {
        return getGroup(groupId).readyPayments().stream().map(Payment::getId).toList();
    }

    /** 결제사에 보냈지만 결론이 안 난 건의 결제사 식별자 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<String> findPendingPgPaymentId(Long groupId, Long paymentId) {
        Payment payment = getGroup(groupId).getPayment(paymentId);
        return payment.isReady() ? Optional.ofNullable(payment.getPgPaymentId()) : Optional.empty();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Long findGroupIdOf(Long paymentId) {
        return paymentGroups.findGroupIdByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_NOT_FOUND));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<Long> findGroupIdByPgPaymentId(String pgPaymentId) {
        return paymentGroups.findGroupIdByPgPaymentId(pgPaymentId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public PaymentResponse findPayment(Long groupId, Long paymentId) {
        PaymentGroup group = getGroup(groupId);
        return PaymentResponse.from(group, group.getPayment(paymentId));
    }

    private PaymentGroup getGroup(Long groupId) {
        return paymentGroups.findById(groupId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_GROUP_NOT_FOUND));
    }

    private PaymentGroup getForUpdate(Long groupId) {
        return paymentGroups.findByIdForUpdate(groupId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_GROUP_NOT_FOUND));
    }
}
