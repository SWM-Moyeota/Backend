package team.codingforest.moyeota.payment.domain.entity;

import jakarta.persistence.*;
import team.codingforest.moyeota.common.BaseTimeEntity;

import java.time.Instant;

@Entity
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
}
