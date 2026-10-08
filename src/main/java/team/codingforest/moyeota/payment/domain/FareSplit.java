package team.codingforest.moyeota.payment.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import java.util.ArrayList;
import java.util.List;

/**
 *  요금 분할 정책. 원 단위로 균등 분할하고 나머지는 앞 사람부터 1원씩 더 낸다 - 합계가 항상 요금과 같아 잔액 계산이 어긋나지 않는다.
 *  예) 10,000원 / 3명 → 3,334 / 3,333 / 3,333
 */
public final class FareSplit {
    private FareSplit() {}

    public static List<Integer> evenly(int fare, int count) {
        if(fare <= 0) throw new BusinessException(PaymentErrorCode.INVALID_FARE);
        if(count <= 0) throw new BusinessException(PaymentErrorCode.INVALID_PASSENGER_COUNT);

        int base = fare / count;
        int remainder = fare % count;

        List<Integer> shares = new ArrayList<>(count);
        for(int i = 0; i < count; i++) {
            shares.add(base + (i < remainder ? 1 : 0));
        }

        return shares;
    }
}
