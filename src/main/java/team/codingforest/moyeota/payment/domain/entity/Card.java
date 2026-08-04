package team.codingforest.moyeota.payment.domain.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class Card {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false)
    private String card_name;

    @Column(nullable = false)
    private String card_number;

    @Column(nullable = false)
    private LocalDateTime created_at;
}
