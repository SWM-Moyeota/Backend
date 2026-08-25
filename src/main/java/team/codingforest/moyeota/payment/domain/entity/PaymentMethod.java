package team.codingforest.moyeota.payment.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;

import java.time.Instant;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "payment_method")
public class PaymentMethod extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String billingKey;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentMethodType type;

    private Instant deletedAt;

    private PaymentMethod(String billingKey, Long userId, PaymentMethodType type, Instant deletedAt) {
        this.billingKey = billingKey;
        this.userId = userId;
        this.type = type;
        this.deletedAt = deletedAt;
    }

    public static PaymentMethod from(String billingKey, Long userId, PaymentMethodType type, Instant deletedAt) {
        return new PaymentMethod(billingKey, userId, type, deletedAt);
    }
}
