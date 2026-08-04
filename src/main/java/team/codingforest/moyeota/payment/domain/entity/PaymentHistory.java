package team.codingforest.moyeota.payment.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Table(name="payment_history")
@Entity
public class PaymentHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long historyId;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false)
    private String order_name;

    @Column(nullable = false)
    private LocalDateTime canceled_at;

    @Column(nullable = false)
    private String fail_code;
}
