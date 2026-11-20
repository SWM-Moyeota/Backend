package team.codingforest.moyeota.payment.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import team.codingforest.moyeota.common.TimeTrackable;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.dto.CardDetail;
import team.codingforest.moyeota.payment.entity.enums.PaymentMethodType;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import java.time.Instant;

/**
 *  유저가 등록한 자동결제(빌링키) 수단. 지금은 카드만 받는다.
 *  여러 활성 카드를 보관하며 기본 결제수단은 하나만 지정한다.
 *  카드 표시 정보는 card 테이블에 PK 공유(1:1)로 둔다 - ERD 그대로. 종류가 늘면 easy_pay / mobile 테이블이 같은 식으로 붙는다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Table(name = "payment_method", indexes = @Index(name = "idx_payment_method_user_id", columnList = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentMethod implements TimeTrackable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_method_id")
    private Long id;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @Column(nullable = false, unique = true)
    private String billingKey;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethodType type;

    private Instant deletedAt;

    @Column(name = "is_default", nullable = false)
    private boolean defaultMethod;

    @OneToOne(mappedBy = "method", cascade = CascadeType.ALL, orphanRemoval = true)
    private Card card;

    private PaymentMethod(Long userId, String billingKey, PaymentMethodType type) {
        this.userId = userId;
        this.billingKey = billingKey;
        this.type = type;
    }

    public static PaymentMethod registerCard(Long userId, String billingKey, CardDetail card) {
        if(userId == null) throw new BusinessException(PaymentErrorCode.PAYMENT_USER_ID_REQUIRED);
        if(billingKey == null || billingKey.isBlank()) throw new BusinessException(PaymentErrorCode.BILLING_KEY_REQUIRED);
        if(card == null) throw new BusinessException(PaymentErrorCode.CARD_INFO_REQUIRED);

        PaymentMethod method = new PaymentMethod(userId, billingKey, PaymentMethodType.CARD);
        method.card = Card.of(method, card);
        return method;
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
}
