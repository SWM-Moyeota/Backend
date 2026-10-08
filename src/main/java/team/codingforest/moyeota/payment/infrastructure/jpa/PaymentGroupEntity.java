package team.codingforest.moyeota.payment.infrastructure.jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.common.BaseTimeEntity;
import team.codingforest.moyeota.payment.domain.Payment;
import team.codingforest.moyeota.payment.domain.PaymentGroup;
import team.codingforest.moyeota.payment.domain.PaymentGroupStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Getter
@Table(name = "payment_group")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentGroupEntity extends BaseTimeEntity {

    /** 방(match_room) id - 방 하나에 결제 그룹 하나 */
    @Column(name = "match_id", nullable = false, unique = true)
    private Long partyId;

    /** 정산 대상 기사 */
    @Column(name = "partner_id", nullable = false)
    private Long driverId;

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

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PaymentEntity> payments = new ArrayList<>();

    private PaymentGroupEntity(Long partyId, Long driverId, Integer fare, Integer passengerCount, Integer remainingBalance, Integer platformCharge, PaymentGroupStatus status) {
        this.partyId = partyId;
        this.driverId = driverId;
        this.fare = fare;
        this.passengerCount = passengerCount;
        this.remainingBalance = remainingBalance;
        this.platformCharge = platformCharge;
        this.status = status;
    }

    public static PaymentGroupEntity from(PaymentGroup group) {
        PaymentGroupEntity entity = new PaymentGroupEntity(group.getPartyId(), group.getDriverId(), group.getFare(), group.getPassengerCount(),
                group.getRemainingBalance(), group.getPlatformCharge(), group.getStatus());

        for(Payment payment : group.getPayments()) {
            entity.payments.add(PaymentEntity.from(entity, payment));
        }

        return entity;
    }

    public PaymentGroup toDomain() {
        return PaymentGroup.restore(getId(), partyId, driverId, fare, passengerCount, platformCharge, getCreatedAt(), remainingBalance, status,
                payments.stream().map(PaymentEntity::toDomain).toList());
    }

    public void update(PaymentGroup group) {
        this.remainingBalance = group.getRemainingBalance();
        this.status = group.getStatus();

        Map<Long, PaymentEntity> byId = new HashMap<>();
        for(PaymentEntity e : payments) byId.put(e.getId(), e);

        for(Payment payment : group.getPayments()) {
            if(payment.getId() == null) {
                payments.add(PaymentEntity.from(this, payment));
            }
            else {
                PaymentEntity existing = byId.get(payment.getId());
                if(existing != null) existing.update(payment);
            }
        }
    }
}
