package team.codingforest.moyeota.payment.event;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.payment.client.PortOneClient;
import team.codingforest.moyeota.payment.repository.PaymentMethodRepository;

@Component
@RequiredArgsConstructor
public class PaymentMethodReplacementListener {
    private final PaymentMethodRepository paymentMethodRepository;
    private final PortOneClient portOneClient;

    @ApplicationModuleListener
    public void on(PaymentMethodReplaced event) {
        paymentMethodRepository.findById(event.methodId()).filter(method -> !method.isActive())
                .ifPresent(method -> portOneClient.deleteBillingKey(method.getBillingKey()));
        // 삭제 실패를 삼키지 않는다. 미완료 이벤트는 공통 EventResubmitter가 재시도한다.
    }
}
