package team.codingforest.moyeota.payment.application;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import team.codingforest.moyeota.payment.domain.PaymentGateway;
import team.codingforest.moyeota.payment.domain.PaymentMethods;

@Component
@RequiredArgsConstructor
public class PaymentMethodReplacementListener {
    private final PaymentMethods methods;
    private final PaymentGateway gateway;

    @ApplicationModuleListener
    public void on(PaymentMethodReplaced event) {
        methods.findById(event.methodId()).filter(method -> !method.isActive())
                .ifPresent(method -> gateway.deleteBillingKey(method.getBillingKey()));
        // 삭제 실패를 삼키지 않는다. 미완료 이벤트는 공통 EventResubmitter가 재시도한다.
    }
}
