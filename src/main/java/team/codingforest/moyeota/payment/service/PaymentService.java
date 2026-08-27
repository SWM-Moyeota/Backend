package team.codingforest.moyeota.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.payment.domain.entity.Currency;
import team.codingforest.moyeota.payment.domain.entity.Payment;
import team.codingforest.moyeota.payment.domain.entity.PaymentMethod;
import team.codingforest.moyeota.payment.domain.entity.PaymentType;
import team.codingforest.moyeota.payment.dto.PaymentVerifyRequest;
import team.codingforest.moyeota.payment.dto.PortOneBillingPaymentRequest;
import team.codingforest.moyeota.payment.dto.PortOneBillingPaymentResponse;
import team.codingforest.moyeota.payment.repository.PaymentMethodRepository;
import team.codingforest.moyeota.payment.repository.PaymentRepository;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private final PortOneBillingClient portOneBillingClient;
    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private static String VERIFY_ORDER_NAME = "모여타 결제 검증";

    @Transactional
    public void payVerify(PaymentVerifyRequest request) {
        PaymentMethod paymentMethod = paymentMethodRepository.findById(request.paymentMethodId())
                .orElseThrow(() -> new IllegalArgumentException("등록되지 않은 결제 수단입니다."));


        int amount = 0;
        Payment payment = Payment.pending(request.userId(), null, request.paymentMethodId(), PaymentType.VERIFICATION, amount, VERIFY_ORDER_NAME);

        paymentRepository.save(payment);

        PortOneBillingPaymentRequest portOneBillingPaymentRequest = new PortOneBillingPaymentRequest(paymentMethod.getBillingKey(), VERIFY_ORDER_NAME, amount, Currency.KRW);

        PortOneBillingPaymentResponse response = portOneBillingClient.payBillingKey(String.valueOf(payment.getId()), portOneBillingPaymentRequest);


    }

     //결제 그룹을 먼저 생성하고 결제
    /*
    public Payment pay(PaymentReq req) {
        List<String> b
    }
    */
}
