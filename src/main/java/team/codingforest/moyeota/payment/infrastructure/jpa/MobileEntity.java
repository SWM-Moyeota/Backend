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
import team.codingforest.moyeota.payment.domain.MobileDetail;

@Entity
@Getter
@Table(name = "mobile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MobileEntity {

    @Id
    private Long paymentMethodId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethodEntity method;

    private String phoneNumber;

    private MobileEntity(PaymentMethodEntity method, String phoneNumber) {
        this.method = method;
        this.phoneNumber = phoneNumber;
    }

    public static MobileEntity of(PaymentMethodEntity method, MobileDetail mobile) {
        return new MobileEntity(method, mobile.phoneNumber());
    }

    public MobileDetail toDomain() {
        return new MobileDetail(phoneNumber);
    }
}
