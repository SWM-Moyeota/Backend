package team.codingforest.moyeota.payment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.domain.ChargeOrder;
import team.codingforest.moyeota.payment.domain.ChargeResult;
import team.codingforest.moyeota.payment.domain.Payment;
import team.codingforest.moyeota.payment.domain.PaymentGateway;
import team.codingforest.moyeota.payment.domain.PaymentGroup;
import team.codingforest.moyeota.payment.domain.PaymentGroups;
import team.codingforest.moyeota.payment.dto.PaymentGroupResponse;
import team.codingforest.moyeota.payment.dto.PaymentResponse;
import team.codingforest.moyeota.payment.dto.RideCompletedCommand;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 *  분할 결제 오케스트레이션. 결제사 호출이 섞이는 메서드(openForRide/retry/cover/sync)는 일부러 트랜잭션을 걸지 않는다 -
 *  DB 작업은 PaymentChargeSteps 의 독립 트랜잭션으로만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentGroupService {
    private final PaymentGroups paymentGroups;
    private final PaymentGateway gateway;
    private final PaymentChargeSteps steps;
    private final UserAccess userAccess;

    /**
     *  운행 종료 → 결제 그룹 생성 + 멤버 전원 청구. 이벤트 재전송에 대비해 멱등 - 이미 그룹이 있으면 남은 청구만 마저 처리한다.
     */
    public void openForRide(RideCompletedCommand command) {
        Long groupId = steps.open(command);

        for(Long paymentId : steps.findReadyPaymentIds(groupId)) {
            charge(groupId, paymentId);
        }
    }

    /**
     *  실패한 본인 분담금 재청구 (결제수단 교체 후)
     */
    public PaymentResponse retry(Long paymentId, Long requesterId) {
        Long groupId = steps.findGroupIdOf(paymentId);

        steps.markRetry(groupId, paymentId, requesterId);
        charge(groupId, paymentId);

        return steps.findPayment(groupId, paymentId);
    }

    /**
     *  미수금 전액을 멤버 한 명이 대납
     */
    public PaymentResponse cover(Long groupId, Long payerId) {
        Long coverId = steps.openCover(groupId, payerId);

        charge(groupId, coverId);

        return steps.findPayment(groupId, coverId);
    }

    /**
     *  결제사 웹훅 - 결제사 원본을 다시 조회해 반영 (웹훅 본문은 믿지 않는다)
     */
    public void sync(String pgPaymentId) {
        Optional<Long> groupId = steps.findGroupIdByPgPaymentId(pgPaymentId);

        if(groupId.isEmpty()) {
            log.warn("우리 기록에 없는 결제 통지 - 무시 pgPaymentId={}", pgPaymentId);
            return;
        }

        steps.sync(groupId.get(), pgPaymentId, gateway.findPayment(pgPaymentId));
    }

    @Transactional(readOnly = true)
    public PaymentGroupResponse findGroup(Long groupId, Long requesterId) {
        PaymentGroup group = paymentGroups.findById(groupId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_GROUP_NOT_FOUND));

        if(!group.hasMember(requesterId)) throw new BusinessException(PaymentErrorCode.NOT_GROUP_MEMBER);

        return PaymentGroupResponse.from(group);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> findMyPayments(Long userId) {
        List<PaymentResponse> result = new ArrayList<>();

        for(PaymentGroup group : paymentGroups.findAllByUserId(userId)) {
            for(Payment payment : group.getPayments()) {
                if(payment.isOwnedBy(userId)) result.add(PaymentResponse.from(group, payment));
            }
        }

        return result;
    }

    /**
     *  청구 1건. 이미 결제사에 보냈는데 결론이 없는 건은 다시 보내지 않고 결제사 원본으로 맞춘다.
     *  결제사 통신 장애(GATEWAY_ERROR)는 삼키지 않는다 - READY+pgPaymentId 로 남고, 리스너가 이벤트를 미완료로 남겨 재전송 때 위 복구 경로를 탄다.
     */
    private void charge(Long groupId, Long paymentId) {
        Optional<String> pending = steps.findPendingPgPaymentId(groupId, paymentId);
        if(pending.isPresent()) {
            steps.sync(groupId, pending.get(), gateway.findPayment(pending.get()));
            return;
        }

        Optional<ChargeOrder> order = steps.prepare(groupId, paymentId);
        if(order.isEmpty()) return;

        ChargeResult result = gateway.charge(order.get().withCustomerId(customerIdOf(order.get().userId())));
        steps.record(groupId, paymentId, result);

        log.info("청구 결과 groupId={}, paymentId={}, paid={}, failCode={}", groupId, paymentId, result.paid(), result.failCode());
    }

    private String customerIdOf(Long userId) {
        MemberSummary summary = userAccess.findMemberSummaries(List.of(userId)).get(userId);
        return summary == null || summary.publicId() == null ? null : summary.publicId().toString();
    }
}
