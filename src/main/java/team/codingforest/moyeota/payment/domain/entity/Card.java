package team.codingforest.moyeota.payment.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class Card {
    @Id
    @Column(nullable = false)
    private Long paymentMethodId;

    @Column(nullable = false)
    private String card_name;

    @Column(nullable = false)
    private String card_number;
}
