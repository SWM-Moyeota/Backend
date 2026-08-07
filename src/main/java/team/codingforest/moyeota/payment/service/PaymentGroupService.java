package team.codingforest.moyeota.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.payment.domain.entity.Payment;
import team.codingforest.moyeota.payment.domain.entity.PaymentGroup;
import team.codingforest.moyeota.payment.domain.entity.PaymentGroupStatus;
import team.codingforest.moyeota.payment.dto.PaymentGroupReq;
import team.codingforest.moyeota.payment.repository.PaymentGroupRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentGroupService {
    private final PaymentGroupRepository paymentGroupRepository;

    public Payment createGroup(PaymentGroupReq req) {
        List<Long> users = req.users();

        //req.partnerId(), totalFare, Integer passengerCount, Integer platformCharge, PaymentGroupStatus status

        //paymentGroup이라는 리스트를 만들어서,  그걸 람다식으로 static 메소드 활용해서 저장하기
        List<PaymentGroup> paymentGroups=req.users().stream().map(p->PaymentGroup.from(req.matchId(), req.total_fare());
        ))



    }
}
