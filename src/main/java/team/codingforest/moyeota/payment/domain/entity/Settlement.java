package team.codingforest.moyeota.payment.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
public class Settlement {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private Long paymentGroupId;

    @Column(nullable = false)
    private int settlementAmount;

    @Column(nullable = false)
    private Integer totalFare;

    @Column(nullable = false)
    private int platformPromotion;
}
