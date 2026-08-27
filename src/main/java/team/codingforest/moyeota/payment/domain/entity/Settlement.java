package team.codingforest.moyeota.payment.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import team.codingforest.moyeota.common.BaseTimeEntity;

@Entity
public class Settlement extends BaseTimeEntity {

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
