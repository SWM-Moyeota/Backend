package team.codingforest.moyeota.payment.infrastructure.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import team.codingforest.moyeota.payment.domain.CardDetail;

@Entity
@Getter
@Table(name = "card")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardEntity {

    @Id
    private Long paymentMethodId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethodEntity method;

    private String name;

    private String number;

    private String brand;

    private CardEntity(PaymentMethodEntity method, String name, String number, String brand) {
        this.method = method;
        this.name = name;
        this.number = number;
        this.brand = brand;
    }

    public static CardEntity of(PaymentMethodEntity method, CardDetail card) {
        return new CardEntity(method, card.name(), card.number(), card.brand());
    }

    public CardDetail toDomain() {
        return new CardDetail(name, number, brand);
    }
}
