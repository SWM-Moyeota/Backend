package team.codingforest.moyeota.payment.entity;

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
import team.codingforest.moyeota.payment.dto.CardDetail;

/**
 *  사용자에게 보여줄 카드 표시 정보. 빌링키 자체는 PaymentMethod 가 든다.
 *  number 는 포트원 조회 응답을 클라이언트에서 다시 마스킹한 표시용 값만 저장한다. 앱으로부터 원본 번호를 받지 않는다.
 */
@Entity
@Getter
@Table(name = "card")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Card {

    @Id
    private Long paymentMethodId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod method;

    private String name;

    /** 결제사가 마스킹해서 준 번호만 저장 */
    private String number;

    private String brand;

    private Card(PaymentMethod method, String name, String number, String brand) {
        this.method = method;
        this.name = name;
        this.number = number;
        this.brand = brand;
    }

    static Card of(PaymentMethod method, CardDetail card) {
        return new Card(method, card.name(), card.number(), card.brand());
    }

    /** 카드명이 없으면 브랜드로 대신 표시 */
    public String getLabel() {
        return name != null ? name : brand;
    }
}
