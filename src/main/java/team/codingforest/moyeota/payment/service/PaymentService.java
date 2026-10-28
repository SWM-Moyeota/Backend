package team.codingforest.moyeota.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.matching.api.PartyAccess;
import team.codingforest.moyeota.matching.api.dto.PartySummary;
import team.codingforest.moyeota.payment.client.PortOneClient;
import team.codingforest.moyeota.payment.dto.PaymentResultResponse;
import team.codingforest.moyeota.payment.dto.PaymentVerificationResponse;
import team.codingforest.moyeota.payment.entity.PaymentGroup;
import team.codingforest.moyeota.payment.entity.PaymentMethod;
import team.codingforest.moyeota.payment.repository.PaymentGroupRepository;
import team.codingforest.moyeota.payment.repository.PaymentMethodRepository;
import team.codingforest.moyeota.payment.repository.PaymentRepository;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor //필요한가?
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PortOneClient portOneClient;
    private final UserAccess userAccess;
    private final PartyAccess partyAccess;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PaymentGroupRepository paymentGroupRepository;

    @Transactional
    public PaymentResultResponse prePay(Long userId, Long partyId){
        //사용자를 검증한다.
        validateUserExist(userId);

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
        PaymentMethod paymentMethod = paymentMethodRepository.findDefaultByUserId(userId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_METHOD_NOT_FOUND));
        String billingKey = paymentMethod.getBillingKey();
        if (billingKey == null || billingKey.isBlank()) {
            throw new BusinessException(PaymentErrorCode.BILLING_KEY_REQUIRED);
        }

        //방의 결제 그룹을 조회하고, 없으면 생성
        // 최초 사전결제 시 기사는 미배정이며, 인원은 현재 조회 시점의 값이다.
        PaymentGroup paymentGroup = paymentGroupRepository.findByMatchId(partyId)
                .orElseGet(() -> paymentGroupRepository.save(
                        PaymentGroup.create(partyId, null, party.memberCount())));

        // TODO: 기존 그룹에 연결된 payment 행과 PENDING 시작 이력(payment_log)을 저장한다.
        // 외부 결제 호출 전에 두 기록이 함께 커밋되도록 트랜잭션 경계를 분리해야 한다.

        // TODO: 기록 커밋 후 portOneClient로 빌링키 결제를 요청한다.
        // 결과 확인 후 payment의 현재 상태를 변경하고 결과 이력을 추가한다.




        //해당 파티에 해당하는 estimatedFare,
        throw new UnsupportedOperationException("사전결제 처리는 아직 구현되지 않았습니다.");
    }

    @Transactional
    public PaymentVerificationResponse cancelPrepayment(Long userId, Long partymentId){

        throw new UnsupportedOperationException("사전결제 취소 처리는 아직 구현되지 않았습니다.");
    }



    // 사용자 데이터의 존재만 확인한다. 탈퇴 여부 검증은 포함하지 않는다.
    private void validateUserExist(Long userId) {
        if (userId == null) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_USER_NOT_FOUND);
        }

        MemberSummary user = userAccess.findMemberSummaries(List.of(userId)).get(userId);
        if (user == null) {
            throw new BusinessException(PaymentErrorCode.PAYMENT_USER_NOT_FOUND);
        }
    }




}
