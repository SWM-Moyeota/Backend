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
    private Integer totalFare;

    @Column(nullable = false)
    private Integer passengerCount;

    @Column(nullable = false)
    private Integer platformCharge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentGroupStatus status;

    private PaymentGroup(Long matchId, Long partnerId, Integer totalFare, Integer passengerCount, Integer platformCharge,PaymentGroupStatus status) {
        this.matchId = matchId;
        this.partnerId = partnerId;
        this.totalFare = totalFare;
        this.passengerCount = passengerCount;
        this.platformCharge = platformCharge;
        this.status = PaymentGroupStatus.PENDING;
    }

    public static PaymentGroup from(Long matchId, Long partnerId, Integer totalFare, Integer passengerCount, Integer platformCharge, PaymentGroupStatus status) {
        return new PaymentGroup(matchId, partnerId, totalFare, passengerCount, platformCharge, status);
    }
}
