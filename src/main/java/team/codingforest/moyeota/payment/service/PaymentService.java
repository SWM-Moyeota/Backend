package team.codingforest.moyeota.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import team.codingforest.moyeota.payment.dto.portone.PortOnePaymentResponse;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.dto.PartySummary;
import team.codingforest.moyeota.payment.client.PortOneClient;
import team.codingforest.moyeota.payment.dto.PaymentResultResponse;
import team.codingforest.moyeota.payment.dto.PaymentVerificationResponse;
import team.codingforest.moyeota.payment.entity.Payment;
import team.codingforest.moyeota.payment.entity.PaymentGroup;
import team.codingforest.moyeota.payment.entity.PaymentLog;
import team.codingforest.moyeota.payment.entity.PaymentMethod;
import team.codingforest.moyeota.payment.entity.enums.PaymentCurrency;
import team.codingforest.moyeota.payment.entity.enums.PaymentLogStatus;
import team.codingforest.moyeota.payment.entity.enums.PaymentType;
import team.codingforest.moyeota.payment.entity.enums.PaymentStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import team.codingforest.moyeota.payment.repository.PaymentGroupRepository;
import team.codingforest.moyeota.payment.repository.PaymentLogRepository;
import team.codingforest.moyeota.payment.repository.PaymentMethodRepository;
import team.codingforest.moyeota.payment.repository.PaymentRepository;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PortOneClient portOneClient;
    private final PartyAccess partyAccess;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentGroupRepository paymentGroupRepository;
    private final PaymentLogRepository paymentLogRepository;

    private final PlatformTransactionManager transactionManager;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentResultResponse prePay(Long userId, Long partyId){
        //방이 존재하는지 확인하고 예상 요금 등이 담긴 정보를 조회한다.
        if (partyId == null) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_PARTY_NOT_FOUND);
        }
        PartySummary party = partyAccess.findSummary(partyId).orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_PARTY_NOT_FOUND));

        //사용자가 방에 참여하는지 유무를 검사한다.
        if (!partyAccess.hasMemberOnParty(partyId, userId)) {
            throw new BusinessException(PaymentErrorCode.NOT_PARTY_MEMBER);
        }

        //PartyAccess를 통해 조회한 예상 요금을 검증한다.
        Integer estimatedFare = party.estimatedFare();
        if (estimatedFare == null || estimatedFare <= 0) {
            throw new BusinessException(PaymentErrorCode.INVALID_PAYMENT_AMOUNT);
        }

        //기본 결제 수단과 빌링키를 조회한다.
        PaymentMethod paymentMethod = paymentMethodRepository.findDefaultByUserId(userId).orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_METHOD_NOT_FOUND));
        String billingKey = paymentMethod.getBillingKey();
        if (billingKey == null || billingKey.isBlank()) {
            throw new BusinessException(PaymentErrorCode.BILLING_KEY_REQUIRED);
        }

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        // 시작 기록을 함께 저장하고 커밋한 뒤 외부 결제를 요청한다.
        Payment payment = transaction.execute(status -> {
            PaymentGroup group = paymentGroupRepository.findByMatchId(partyId)
                    .orElseGet(() -> paymentGroupRepository.save(
                            PaymentGroup.create(partyId, null, party.memberCount())));

            Payment saved = paymentRepository.save(Payment.create(
                    null, userId, group.getId(), paymentMethod.getId(), estimatedFare,
                    PaymentType.VERIFICATION, PaymentCurrency.KRW, "모여타 결제 가능 여부 검증"));

            paymentLogRepository.save(PaymentLog.create(
                    saved.getId(), saved.getAmount(), PaymentLogStatus.PENDING, saved.getType(), null));

            return saved;
        });

        // DB 기본키를 포트원 요청용 문자열로 변환한다. 응답의 pgTxId와는 다른 값이다.
        String requestPaymentId = payment.getId().toString();
        PortOnePaymentResponse response = portOneClient.payWithBillingKey(
                requestPaymentId, billingKey, payment.getAmount().longValue());

        // 통신 오류나 불완전한 응답은 승인 실패로 단정하지 않고 PENDING을 유지한다.
        if (response == null || response.payment() == null
                || response.payment().pgTxId() == null || response.payment().pgTxId().isBlank()
                || response.payment().paidAt() == null || response.payment().paidAt().isBlank()) {
            throw new BusinessException(PaymentErrorCode.GATEWAY_ERROR);
        }

        return transaction.execute(status -> {
            Payment paid = paymentRepository.findById(payment.getId())
                    .orElseThrow(() -> new IllegalStateException("결제 시작 기록을 찾을 수 없습니다."));
            paid.markPaid(response.payment().pgTxId());
            paymentLogRepository.save(PaymentLog.create(
                    paid.getId(), paid.getAmount(), PaymentLogStatus.PAID, paid.getType(), null));
            return new PaymentResultResponse(
                    paid.getId(), paid.getPgTxId(), paid.getUserId(), paid.getAmount(), paid.getStatus());
        });
    }



    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentVerificationResponse cancelPrepayment(Long userId, Long paymentId){
        if (userId == null || paymentId == null) {
            throw new IllegalArgumentException("userId와 paymentId는 필수입니다.");
        }
        Payment payment = paymentRepository.findById(paymentId)
                .filter(found -> userId.equals(found.getUserId()))
                .orElseThrow(() -> new IllegalArgumentException("본인의 결제 기록을 찾을 수 없습니다."));
        if (payment.getType() != PaymentType.VERIFICATION
                || payment.getStatus() != PaymentStatus.PAID) {
            throw new IllegalArgumentException("승인된 사전결제만 취소할 수 있습니다.");
        }

        // 승인 때와 동일하게 DB 기본키를 문자열로 전달한다.
        String requestPaymentId = payment.getId().toString();
        String response = portOneClient.cancelPayment(requestPaymentId, "사전 결제 취소");
        if (response == null || response.isBlank()) {
            throw new BusinessException(PaymentErrorCode.GATEWAY_ERROR);
        }

        // HTTP 성공만으로 취소 완료를 단정하지 않는다. REQUESTED/FAILED는 성공이 아니다.
        try {
            var mapper = JsonMapper.builder().build();
            var cancellation = mapper.readTree(response).path("cancellation");
            if (!"SUCCEEDED".equals(cancellation.path("status").asText())
                    || !cancellation.path("totalAmount").isIntegralNumber()
                    || payment.getAmount() == null
                    || cancellation.path("totalAmount").asLong() != payment.getAmount().longValue()) {
                throw new BusinessException(PaymentErrorCode.GATEWAY_ERROR);
            }
        } catch (JacksonException e) {
            throw new BusinessException(PaymentErrorCode.GATEWAY_ERROR);
        }

        // 외부 취소 완료 확인 후, 별도 트랜잭션에서 관리되는 엔티티를 조회해 상태를 반영한다.
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            Payment canceled = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new IllegalStateException("취소할 결제 기록을 찾을 수 없습니다."));
            canceled.markCanceled();
            return new PaymentVerificationResponse(canceled.getId(), true);
        });
    }
}
