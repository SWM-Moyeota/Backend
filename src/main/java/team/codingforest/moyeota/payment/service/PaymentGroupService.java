package team.codingforest.moyeota.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.payment.domain.entity.Payment;
import team.codingforest.moyeota.payment.domain.entity.PaymentGroup;
import team.codingforest.moyeota.payment.dto.PaymentGroupReq;
import team.codingforest.moyeota.payment.repository.PaymentGroupRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentGroupService {
    private final PaymentGroupRepository paymentGroupRepository;

    public Payment createGroup(PaymentGroupReq req) {
        List<Long> users = req.users();

        paymentGroupRepository.save(PaymentGroup.from(users.stream()))
    }
}
