package team.codingforest.moyeota.payment.application;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 운영 설정값. platform-charge 는 분담금 1건마다 얹는 플랫폼 이용료(원) - 0 이면 요금만 받는다 */
@Getter
@Component
public class PaymentPolicy {
    private final int platformCharge;

    public PaymentPolicy(@Value("${moyeota.payment.platform-charge:0}") int platformCharge) {
        if(platformCharge < 0) throw new IllegalArgumentException("moyeota.payment.platform-charge 는 0 이상이어야 합니다: " + platformCharge);
        this.platformCharge = platformCharge;
    }
}
