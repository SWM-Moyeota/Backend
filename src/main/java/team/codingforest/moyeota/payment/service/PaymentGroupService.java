package team.codingforest.moyeota.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.payment.domain.entity.Payment;
import team.codingforest.moyeota.payment.domain.entity.PaymentGroup;
import team.codingforest.moyeota.payment.domain.entity.PaymentType;
import team.codingforest.moyeota.payment.dto.PaymentGroupReq;
import team.codingforest.moyeota.payment.repository.PaymentGroupRepository;
import team.codingforest.moyeota.payment.repository.PaymentRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentGroupService {
    private final PaymentGroupRepository paymentGroupRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public PaymentGroup createGroup(PaymentGroupReq req) {
        PaymentGroup paymentGroup = PaymentGroup.from(
                req.matchId(),
                req.partnerId(),
                calculateFare(req.totalFare(), req.passengerCount()),
                req.passengerCount(),
                calculateBalance(req.totalFare(), req.passengerCount()),
                calculatePlatformCharge(req.totalFare())
                );

        paymentGroupRepository.save(paymentGroup);

//        List<Payment> payments = req.userIds().stream()
//                    .map(userId -> Payment.pendingMain(
//                            userId,
//                            paymentGroup.getId(),
//
//                    ))
//                ;

        return paymentGroupRepository.save(paymentGroup);
    }

    // TODO 예외처리 해야함
    @Transactional(readOnly = true)
    public PaymentGroup findById(Long id) {
        return paymentGroupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 그룹입니다."));
    }

    // TODO 배치 처리 고려 현재는 COMPLETE만 했음
    @Transactional
    public void updateStatus(Long id) {
        List<Payment> payments = paymentRepository.findByPaymentGroupId(id, PaymentType.MAIN);
        PaymentGroup payment = paymentGroupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 그룹입니다."));

        if(payment.getPassengerCount() == payments.size()) {
            payment.updateStatus();
        }
    }

    private Integer calculatePlatformCharge(Integer totalFare) {
        if (totalFare <= 10000) {
            return 1000;
        }
        else return 2000;
    }

    private Integer calculateFare(Integer totalFare, Integer passengerCount) {
        return (totalFare / passengerCount) / 10 * 10;
    }

    private Integer calculateBalance(Integer totalFare, Integer passengerCount) {
        return totalFare - (calculateFare(totalFare, passengerCount) * passengerCount);
    }
}
