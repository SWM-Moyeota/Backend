package team.codingforest.moyeota.payment.infrastructure.jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.AttributeOverride;
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
import team.codingforest.moyeota.payment.domain.PaymentMethod;
import team.codingforest.moyeota.payment.domain.PaymentMethodType;

import java.time.Instant;

/** 카드 표시 정보는 card 테이블에 PK 공유(1:1)로 둔다 - ERD 그대로. 종류가 늘면 easy_pay / mobile 테이블이 같은 식으로 붙는다 */
@Entity
@AttributeOverride(name = "id", column = @Column(name = "payment_method_id"))
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

    @Column(name = "is_default", nullable = false)
    private boolean defaultMethod;

    @OneToOne(mappedBy = "method", cascade = CascadeType.ALL, orphanRemoval = true)
    private CardEntity card;

    private PaymentMethodEntity(String billingKey, Long userId, PaymentMethodType type, Instant deletedAt) {
        this.billingKey = billingKey;
        this.userId = userId;
        this.type = type;
        this.deletedAt = deletedAt;
    }

    public static PaymentMethodEntity from(PaymentMethod method) {
        PaymentMethodEntity entity = new PaymentMethodEntity(method.getBillingKey(), method.getUserId(), method.getType(), method.getDeletedAt());
        entity.card = CardEntity.of(entity, method.getCard());
        entity.defaultMethod = method.isDefaultMethod();

        return entity;
    }

    public PaymentMethod toDomain() {
        return PaymentMethod.restore(getId(), userId, billingKey, card.toDomain(), getCreatedAt(), deletedAt, defaultMethod);
    }

    /** 빌링키·카드 정보는 불변 - 교체는 새 수단 등록으로 한다. 삭제 여부와 기본 카드 여부를 변경한다 */
    public void update(PaymentMethod method) {
        this.deletedAt = method.getDeletedAt();
        this.defaultMethod = method.isDefaultMethod();
    }
}
