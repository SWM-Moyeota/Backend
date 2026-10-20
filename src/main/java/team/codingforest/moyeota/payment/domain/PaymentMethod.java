package team.codingforest.moyeota.payment.domain;

import lombok.Getter;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import java.time.Instant;

/**
 *  애그리거트 루트 - 유저가 등록한 자동결제(빌링키) 수단. 지금은 카드만 받는다.
 *  여러 활성 카드를 보관하며 기본 결제수단은 하나만 지정한다.
 */
@Getter
public class PaymentMethod {
    private final Long id;
    private final Long userId;
    private final String billingKey;
    private final CardDetail card;
    private final Instant createdAt;
    private Instant deletedAt;
    private boolean defaultMethod;

    private PaymentMethod(Long id, Long userId, String billingKey, CardDetail card, Instant createdAt, Instant deletedAt) {
        this.id = id;
        this.userId = userId;
        this.billingKey = billingKey;
        this.card = card;
        this.createdAt = createdAt;
        this.deletedAt = deletedAt;
    }

    public static PaymentMethod registerCard(Long userId, String billingKey, CardDetail card, Instant now) {
        if(userId == null) throw new BusinessException(PaymentErrorCode.PAYMENT_USER_ID_REQUIRED);
        if(billingKey == null || billingKey.isBlank()) throw new BusinessException(PaymentErrorCode.BILLING_KEY_REQUIRED);
        if(card == null) throw new BusinessException(PaymentErrorCode.CARD_INFO_REQUIRED);

        return new PaymentMethod(null, userId, billingKey, card, now, null);
    }

    /** 두 번 지워도 처음 지운 시각을 유지한다 */
    public void delete(Instant now) {
        if(deletedAt == null) deletedAt = now;
        defaultMethod = false;
    }

    public void selectAsDefault() {
        if(!isActive()) throw new BusinessException(PaymentErrorCode.PAYMENT_METHOD_DELETED);
        defaultMethod = true;
    }

    public void clearDefault() {
        defaultMethod = false;
    }

    public boolean isActive() {
        return deletedAt == null;
    }

    public boolean hasBillingKey(String billingKey) {
        return this.billingKey.equals(billingKey);
    }

    public PaymentMethodType getType() {
        return PaymentMethodType.CARD;
    }

    public static PaymentMethod restore(Long id, Long userId, String billingKey, CardDetail card, Instant createdAt, Instant deletedAt, boolean defaultMethod) {
        PaymentMethod method = restore(id, userId, billingKey, card, createdAt, deletedAt);
        if(defaultMethod) method.selectAsDefault();
        return method;
    }

    /** 영속 복원용 */
    public static PaymentMethod restore(Long id, Long userId, String billingKey, CardDetail card, Instant createdAt, Instant deletedAt) {
        return new PaymentMethod(id, userId, billingKey, card, createdAt, deletedAt);
    }
}
