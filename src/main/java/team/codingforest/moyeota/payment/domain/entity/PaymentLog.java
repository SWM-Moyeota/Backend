package team.codingforest.moyeota.payment.domain.entity;

import jakarta.persistence.*;
import team.codingforest.moyeota.common.BaseTimeEntity;

@Entity
@Table(name = "payment_log")
public class PaymentLog extends BaseTimeEntity {

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentType type;

    private String failCode;
}
