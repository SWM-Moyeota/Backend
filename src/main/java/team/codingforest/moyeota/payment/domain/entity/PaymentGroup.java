package team.codingforest.moyeota.payment.domain.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class PaymentGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long matchId;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private int totalFare;

    @Column(nullable = false)
    private int passengerCount;

    @Column(nullable = false)
    private Integer platformCharge;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private PaymentGroupStatus status;
}
