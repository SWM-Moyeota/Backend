package team.codingforest.moyeota.faresplit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 *  한 사람의 부담 금액은 1원 이상이어야 만들어진다. 0원·음수는 INVALID_PAYMENT 이고, 같은 사람·같은 금액이면 같은 값이다
 */
class FarePaymentUnitTest {
    private static final Long MEMBER_ID = 2L;
    private static final Long OTHER_MEMBER_ID = 3L;

    @DisplayName("생성 - 1원 이상이면(가장 작은 1원 포함) 누가 얼마를 부담하는지 그대로 담는다")
    @ParameterizedTest(name = "{0}원")
    @ValueSource(ints = {1, 6_240, 500_000})
    void create(int amount) {

        /*
         * test
         */
        FarePayment payment = new FarePayment(MEMBER_ID, amount);

        /*
         * verify
         */
        assertThat(payment.memberId()).isEqualTo(MEMBER_ID);
        assertThat(payment.amount()).isEqualTo(amount);
    }

    @DisplayName("생성 - 0원이나 음수면 INVALID_PAYMENT")
    @ParameterizedTest(name = "{0}원")
    @ValueSource(ints = {0, -1, -6_240})
    void create_notPositive(int amount) {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> new FarePayment(MEMBER_ID, amount));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(FareSplitErrorCode.INVALID_PAYMENT);
    }

    @DisplayName("equals - 같은 사람·같은 금액이면 같고, 사람이나 금액이 다르면 다르다")
    @Test
    void equals() {

        /*
         * settings
         */
        FarePayment payment = new FarePayment(MEMBER_ID, 6_240);

        /*
         * test
         */
        FarePayment same = new FarePayment(MEMBER_ID, 6_240);
        FarePayment otherMember = new FarePayment(OTHER_MEMBER_ID, 6_240);
        FarePayment otherAmount = new FarePayment(MEMBER_ID, 6_241);

        /*
         * verify
         */
        assertThat(payment).isEqualTo(same);
        assertThat(payment).isNotEqualTo(otherMember);
        assertThat(payment).isNotEqualTo(otherAmount);
    }
}
