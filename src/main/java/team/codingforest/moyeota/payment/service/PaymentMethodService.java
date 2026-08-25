package team.codingforest.moyeota.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import team.codingforest.moyeota.payment.domain.entity.PaymentMethod;
import team.codingforest.moyeota.payment.dto.PaymentMethodCommand;
import team.codingforest.moyeota.payment.repository.PaymentMethodRepository;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PaymentMethodService {
    private final PaymentMethodRepository paymentMethodRepository;

    public void save(PaymentMethodCommand command) {
        PaymentMethod method = PaymentMethod.from(command.billingKey(), command.userId(), command.type(), Instant.now());

        paymentMethodRepository.save(method);
    }


}
