package team.codingforest.moyeota.payment.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.payment.exception.PaymentErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FareSplitTest {

    @Test
    void 나누어_떨어지면_균등하다() {
        assertThat(FareSplit.evenly(12000, 3)).containsExactly(4000, 4000, 4000);
    }

    @Test
    void 나머지는_앞_사람부터_1원씩_더_내고_합계는_요금과_같다() {
        // 10원 단위 절삭 같은 걸 하면 합계가 요금과 달라져 잔액이 영영 0 이 안 된다
        assertThat(FareSplit.evenly(10000, 3)).containsExactly(3334, 3333, 3333);
        assertThat(FareSplit.evenly(10001, 2)).containsExactly(5001, 5000);
        assertThat(FareSplit.evenly(7, 4).stream().mapToInt(Integer::intValue).sum()).isEqualTo(7);
    }

    @Test
    void 혼자면_전액이다() {
        assertThat(FareSplit.evenly(9900, 1)).containsExactly(9900);
    }

    @Test
    void 요금이_0_이하면_거부() {
        assertThatThrownBy(() -> FareSplit.evenly(0, 2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_FARE);
    }

    @Test
    void 인원이_0_이하면_거부() {
        assertThatThrownBy(() -> FareSplit.evenly(1000, 0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(PaymentErrorCode.INVALID_PASSENGER_COUNT);
    }
}
