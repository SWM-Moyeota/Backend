package team.codingforest.moyeota.payment.domain.entity;

import jakarta.persistence.*;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.payment.dto.PaymentGroupReq;

import java.time.Instant;

@Entity
@Table(name = "payment_group")
@NoArgsConstructor
public class PaymentGroup extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long matchId;

    @Column(nullable = false)
    private Long partnerId;

    @Column(nullable = false)
    private Integer fare;

    @Column(nullable = false)
    private Integer passengerCount;

    @Column(nullable = false)
    private Integer remainingBalance;

    @Column(nullable = false)
    private Integer platformCharge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentGroupStatus status;

    private PaymentGroup(Long matchId, Long partnerId, Integer fare, Integer passengerCount, Integer remainingBalance, Integer platformCharge) {
        this.matchId = matchId;
        this.partnerId = partnerId;
        this.fare = fare;
        this.passengerCount = passengerCount;
        this.remainingBalance = remainingBalance;
        this.platformCharge = platformCharge;
        this.status = PaymentGroupStatus.PENDING;
    }

    public static PaymentGroup from(Long matchId, Long partnerId, Integer fare, Integer passengerCount, Integer remainingBalance, Integer platformCharge) {
        return new PaymentGroup(matchId, partnerId, fare, passengerCount, remainingBalance, platformCharge);
    }
}
