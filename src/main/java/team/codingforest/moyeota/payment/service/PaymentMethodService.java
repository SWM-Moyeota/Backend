package team.codingforest.moyeota.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.payment.domain.entity.Card;
import team.codingforest.moyeota.payment.domain.entity.PaymentMethod;
import team.codingforest.moyeota.payment.domain.entity.PaymentMethodType;
import team.codingforest.moyeota.payment.dto.PaymentMethodResponse;
import team.codingforest.moyeota.payment.dto.PortOneBillingKeyResponse;
import team.codingforest.moyeota.payment.dto.RegisterPaymentResponse;
import team.codingforest.moyeota.payment.repository.CardRepository;
import team.codingforest.moyeota.payment.repository.PaymentMethodRepository;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentMethodService {
    private final PaymentMethodRepository paymentMethodRepository;
    private final CardRepository cardRepository;
    private final PortOneBillingClient portOneBillingClient;

    @Transactional
    // TODO 예외처리 해야함
    public RegisterPaymentResponse register(Long userId, String billingKey) {
        PortOneBillingKeyResponse info = portOneBillingClient.getBillingKey(billingKey);   // 앱이 준 값 → 반드시 검증

        if(info == null || !info.isIssued()) throw new IllegalArgumentException("유효하지 않은 빌링키입니다.");
        if(!userId.equals(Long.valueOf(info.customer().id()))) {
            throw new IllegalArgumentException("해당 유저가 아닙니다.");
        }

        paymentMethodRepository.findByUserIdAndDeletedAtIsNull(userId)
                .ifPresent(PaymentMethod::delete);                 // 카드 교체

        PortOneBillingKeyResponse.Card card = info.firstCard();

        PaymentMethod paymentMethod = PaymentMethod.from(billingKey, userId, PaymentMethodType.CARD);

        paymentMethodRepository.save(paymentMethod);
        cardRepository.save(Card.of(paymentMethod.getId(), card));

        log.info("결제수단 등록 userId={}, card={}", userId, card == null ? "정보없음" : card.name());   // billingKey 는 로그 금지

        return new RegisterPaymentResponse(paymentMethod.getId());
    }

    @Transactional(readOnly = true)
    public PaymentMethodResponse findActiveCard(Long userId) {
        PaymentMethod method = paymentMethodRepository.findByUserIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new IllegalArgumentException("등록된 카드 없습니다."));

        Card card = cardRepository.findById(method.getId()).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 카드입니다."));

        return PaymentMethodResponse.from(card.getName(), card.getNumber(), card.getBrand());
    }
}
