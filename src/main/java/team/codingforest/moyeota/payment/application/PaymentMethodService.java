package team.codingforest.moyeota.payment.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.domain.BillingKeyInfo;
import team.codingforest.moyeota.payment.domain.PaymentGateway;
import team.codingforest.moyeota.payment.domain.PaymentMethod;
import team.codingforest.moyeota.payment.domain.PaymentMethods;
import team.codingforest.moyeota.payment.dto.PaymentMethodResponse;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentMethodService {
    private final PaymentMethods paymentMethods;
    private final PaymentGateway gateway;
    private final UserAccess userAccess;

    /**
     *  앱이 포트원 SDK 로 발급받은 빌링키를 등록. 앱이 준 값은 믿지 않고 결제사에서 조회해 발급 상태·명의(customer.id = publicId)를 확인한다.
     *  유저당 활성 수단 1개 - 기존 수단은 soft delete 하고 결제사에서도 지운다.
     */
    @Transactional
    public PaymentMethodResponse register(Long userId, String billingKey) {
        BillingKeyInfo info = gateway.inspectBillingKey(billingKey);

        if(!info.issued() || info.detail() == null) throw new BusinessException(PaymentErrorCode.INVALID_BILLING_KEY);
        if(!publicIdOf(userId).equals(info.customerId())) throw new BusinessException(PaymentErrorCode.BILLING_KEY_OWNER_MISMATCH);

        Instant now = Instant.now();
        Optional<PaymentMethod> previous = paymentMethods.findActiveByUserId(userId);

        previous.ifPresent(method -> {
            method.delete(now);
            paymentMethods.save(method);
        });

        PaymentMethod saved = paymentMethods.save(PaymentMethod.register(userId, billingKey, info.detail(), now));

        // 결제사 빌링키 삭제는 되돌릴 수 없으므로 DB 반영이 끝난 뒤 - 실패해도 우리 쪽에선 이미 비활성이라 청구에 쓰이지 않는다
        previous.ifPresent(method -> deleteQuietly(method.getBillingKey()));

        log.info("결제수단 등록 userId={}, methodId={}, type={}", userId, saved.getId(), saved.getType());   // 빌링키는 로그 금지

        return PaymentMethodResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PaymentMethodResponse findMine(Long userId) {
        return paymentMethods.findActiveByUserId(userId)
                .map(PaymentMethodResponse::from)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_METHOD_NOT_FOUND));
    }

    @Transactional
    public void remove(Long userId) {
        PaymentMethod method = paymentMethods.findActiveByUserId(userId)
                .orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_METHOD_NOT_FOUND));

        method.delete(Instant.now());
        paymentMethods.save(method);
        deleteQuietly(method.getBillingKey());

        log.info("결제수단 삭제 userId={}, methodId={}", userId, method.getId());
    }

    private void deleteQuietly(String billingKey) {
        try {
            gateway.deleteBillingKey(billingKey);
        } catch (BusinessException e) {
            log.warn("결제사 빌링키 삭제 실패 - 우리 쪽은 비활성 처리됨 code={}", e.getErrorCode().getCode());
        }
    }

    private String publicIdOf(Long userId) {
        MemberSummary summary = userAccess.findMemberSummaries(List.of(userId)).get(userId);
        if(summary == null || summary.publicId() == null) throw new BusinessException(PaymentErrorCode.BILLING_KEY_OWNER_MISMATCH);

        return summary.publicId().toString();
    }
}
