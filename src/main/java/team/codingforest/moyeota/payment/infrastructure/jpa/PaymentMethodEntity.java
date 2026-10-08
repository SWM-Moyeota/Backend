package team.codingforest.moyeota.payment.infrastructure.jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.payment.domain.CardDetail;
import team.codingforest.moyeota.payment.domain.EasyPayDetail;
import team.codingforest.moyeota.payment.domain.MethodDetail;
import team.codingforest.moyeota.payment.domain.MobileDetail;
import team.codingforest.moyeota.payment.domain.PaymentMethod;
import team.codingforest.moyeota.payment.domain.PaymentMethodType;

import java.time.Instant;

/** 종류별 표시 정보는 card / easy_pay / mobile 테이블에 PK 공유(1:1)로 둔다 - ERD 그대로 */
@Entity
@Getter
@Table(name = "payment_method", indexes = @Index(name = "idx_payment_method_user_id", columnList = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentMethodEntity extends BaseTimeEntity {

    @Column(nullable = false, unique = true)
    private String billingKey;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethodType type;

    private Instant deletedAt;

    @OneToOne(mappedBy = "method", cascade = CascadeType.ALL, orphanRemoval = true)
    private CardEntity card;

    @OneToOne(mappedBy = "method", cascade = CascadeType.ALL, orphanRemoval = true)
    private EasyPayEntity easyPay;

    @OneToOne(mappedBy = "method", cascade = CascadeType.ALL, orphanRemoval = true)
    private MobileEntity mobile;

    private PaymentMethodEntity(String billingKey, Long userId, PaymentMethodType type, Instant deletedAt) {
        this.billingKey = billingKey;
        this.userId = userId;
        this.type = type;
        this.deletedAt = deletedAt;
    }

    public static PaymentMethodEntity from(PaymentMethod method) {
        PaymentMethodEntity entity = new PaymentMethodEntity(method.getBillingKey(), method.getUserId(), method.getType(), method.getDeletedAt());

        switch(method.getDetail()) {
            case CardDetail card -> entity.card = CardEntity.of(entity, card);
            case EasyPayDetail easyPay -> entity.easyPay = EasyPayEntity.of(entity, easyPay);
            case MobileDetail mobile -> entity.mobile = MobileEntity.of(entity, mobile);
        }

        return entity;
    }

    public PaymentMethod toDomain() {
        MethodDetail detail = switch(type) {
            case CARD -> card.toDomain();
            case EASY_PAY -> easyPay.toDomain();
            case MOBILE -> mobile.toDomain();
        };

        return PaymentMethod.restore(getId(), userId, billingKey, detail, getCreatedAt(), deletedAt);
    }

    /** 빌링키·종류는 불변 - 교체는 새 수단 등록으로 한다 */
    public void update(PaymentMethod method) {
        this.deletedAt = method.getDeletedAt();
    }
}
