package team.codingforest.moyeota.payment.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.payment.dto.PortOneBillingKeyResponse;

@Entity
@Getter
@NoArgsConstructor
public class Card {
    @Id
    @Column(nullable = false)
    private Long paymentMethodId;

    private String name;

    private String number;

    private String brand;

    private Card(Long paymentMethodId, String name, String number, String brand) {
        this.paymentMethodId = paymentMethodId;
        this.name = name;
        this.number = number;
        this.brand = brand;
    }

    public static Card of(Long paymentMethodId, PortOneBillingKeyResponse.Card card) {
        return new Card(paymentMethodId,
                card == null? null : card.name(),
                card == null? null : card.number(),
                card == null? null : card.brand());
    }
}
