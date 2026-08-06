package team.codingforest.moyeota.payment.domain.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Table(name="payment")
@Entity
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long id;

    @Column(nullable = false)
    private String pgPaymentId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long paymentGroupId;

    @Column(nullable = false)
    private Long paymentMethodId;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false)
    private String orderName;

    private String failCode;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updateAt;

    private Instant cancelAt;

    private String cancelReason;
}
