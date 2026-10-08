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
import team.codingforest.moyeota.payment.domain.EasyPayDetail;

@Entity
@Getter
@Table(name = "easy_pay")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EasyPayEntity {

    @Id
    private Long paymentMethodId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethodEntity method;

    /** 간편결제사(KAKAOPAY, NAVERPAY, TOSSPAY ...) */
    private String provider;

    private EasyPayEntity(PaymentMethodEntity method, String provider) {
        this.method = method;
        this.provider = provider;
    }

    public static EasyPayEntity of(PaymentMethodEntity method, EasyPayDetail easyPay) {
        return new EasyPayEntity(method, easyPay.provider());
    }

    public EasyPayDetail toDomain() {
        return new EasyPayDetail(provider);
    }
}
