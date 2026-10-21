package team.codingforest.moyeota.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.client.PortOneClient;
import team.codingforest.moyeota.payment.dto.BillingKeyInfo;
import team.codingforest.moyeota.payment.dto.PaymentMethodResponse;
import team.codingforest.moyeota.payment.entity.PaymentMethod;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;
import team.codingforest.moyeota.payment.repository.PaymentMethodRepository;
import team.codingforest.moyeota.user.api.MemberSummary;
import team.codingforest.moyeota.user.api.UserAccess;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentMethodService {
    private final PaymentMethodRepository paymentMethodRepository;
    private final PortOneClient portOneClient;
    private final UserAccess userAccess;

    /**
     *  앱이 포트원 SDK 로 발급받은 빌링키를 카드 결제수단으로 등록.
     *  결제사 조회 결과의 customer.id와 로그인 사용자의 publicId를 비교한다. 카드 명의 인증을 뜻하지 않는다.
     *  첫 카드는 기본 카드가 되며 이후 등록은 기존 카드와 기본 선택을 유지한다.
     */
    @Transactional
    public PaymentMethodResponse register(Long userId, String billingKey) {
        String customerId = publicIdOf(userId);
        if(billingKey == null || billingKey.isBlank() || billingKey.length() > 255) {
            throw new BusinessException(PaymentErrorCode.INVALID_BILLING_KEY);
        }
        BillingKeyInfo info = portOneClient.inspectBillingKey(billingKey);

        if(!info.issued()) throw new BusinessException(PaymentErrorCode.INVALID_BILLING_KEY);
        if(!info.isCard()) throw new BusinessException(PaymentErrorCode.UNSUPPORTED_PAYMENT_METHOD);
        if(!customerId.equals(info.customerId())) throw new BusinessException(PaymentErrorCode.BILLING_KEY_OWNER_MISMATCH);

        List<PaymentMethod> existing = paymentMethodRepository.findAllActiveByUserIdForUpdate(userId);
        Optional<PaymentMethod> registered = paymentMethodRepository.findByBillingKey(billingKey);
        if(registered.isPresent()) {
            PaymentMethod method = registered.get();
            // 교체되어 삭제 대기 중인 키는 다시 활성화하지 않는다.
            if(!method.getUserId().equals(userId) || !method.isActive()) {
                throw new BusinessException(PaymentErrorCode.PAYMENT_METHOD_CONFLICT);
            }
            return PaymentMethodResponse.from(method);
        }

        PaymentMethod method = PaymentMethod.registerCard(userId, billingKey, info.card());
        if(existing.stream().noneMatch(PaymentMethod::isDefaultMethod)) method.selectAsDefault();
        PaymentMethod saved = saveNew(method);
        log.info("카드 결제수단 등록 userId={}, methodId={}", userId, saved.getId());

        return PaymentMethodResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodResponse> list(Long userId) {
        publicIdOf(userId);
        return paymentMethodRepository.findAllActiveByUserId(userId).stream()
                .map(PaymentMethodResponse::from).toList();
    }

    @Transactional
    public PaymentMethodResponse selectDefault(Long userId, Long methodId) {
        publicIdOf(userId);
        List<PaymentMethod> methods = paymentMethodRepository.findAllActiveByUserIdForUpdate(userId);
        PaymentMethod selected = methods.stream().filter(method -> method.getId().equals(methodId))
                .findFirst().orElseThrow(() -> new BusinessException(PaymentErrorCode.PAYMENT_METHOD_NOT_FOUND));
        if(selected.isDefaultMethod()) return PaymentMethodResponse.from(selected);
        methods.stream().filter(PaymentMethod::isDefaultMethod).forEach(PaymentMethod::clearDefault);
        // 기존 기본 카드 해제를 먼저 반영해야 새 기본 카드 지정이 UNIQUE 제약과 충돌하지 않는다.
        paymentMethodRepository.flush();
        selected.selectAsDefault();
        return PaymentMethodResponse.from(selected);
    }

    private PaymentMethod saveNew(PaymentMethod method) {
        try {
            return paymentMethodRepository.saveAndFlush(method);
        } catch (DataIntegrityViolationException e) {
            // 동시 등록과 이미 사용한 빌링키는 500 대신 409. 서비스 트랜잭션 전체를 롤백한다.
            for(Throwable cause = e; cause != null; cause = cause.getCause()) {
                if(cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new BusinessException(PaymentErrorCode.PAYMENT_METHOD_CONFLICT);
                }
            }
            throw e;
        }
    }

    private String publicIdOf(Long userId) {
        if(userId == null) throw new BusinessException(PaymentErrorCode.PAYMENT_USER_NOT_FOUND);
        MemberSummary summary = userAccess.findMemberSummaries(List.of(userId)).get(userId);
        if(summary == null || summary.publicId() == null) throw new BusinessException(PaymentErrorCode.PAYMENT_USER_NOT_FOUND);

        return summary.publicId().toString();
    }
}
