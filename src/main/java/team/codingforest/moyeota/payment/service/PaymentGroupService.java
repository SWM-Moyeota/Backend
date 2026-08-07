package team.codingforest.moyeota.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.payment.domain.entity.PaymentGroup;
import team.codingforest.moyeota.payment.dto.PaymentGroupReq;
import team.codingforest.moyeota.payment.repository.PaymentGroupRepository;

@Service
@RequiredArgsConstructor
public class PaymentGroupService {
    private final PaymentGroupRepository paymentGroupRepository;

    public PaymentGroup createGroup(PaymentGroupReq req) {
        int fare = (req.totalFare() / req.passengerCount()) / 10 * 10;
        int remainingBalance = req.totalFare() - (fare * req.passengerCount());

        PaymentGroup paymentGroup = PaymentGroup.from(
                req.matchId(),
                req.partenrId(),
                fare,
                req.passengerCount(),
                remainingBalance,
                calculatePlatformCharge(req.totalFare())
                );

        return paymentGroupRepository.save(paymentGroup);
    }

    private Integer calculatePlatformCharge(Integer totalFare) {
        if (totalFare <= 10000) {
            return 1000;
        }
        else return 2000;
    }
}
