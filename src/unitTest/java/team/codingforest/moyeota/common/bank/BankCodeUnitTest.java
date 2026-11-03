package team.codingforest.moyeota.common.bank;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class BankCodeUnitTest {

    @DisplayName("tossCode - 모든 은행이 토스 두 자리 숫자 코드를 가진다")
    @Test
    void tossCode_twoDigits() {

        /*
         * test, verify
         */
        assertThat(BankCode.values())
                .extracting(BankCode::getTossCode)
                .allMatch(code -> code != null && code.matches("\\d{2}"));
    }

    @DisplayName("tossCode - 은행끼리 토스 코드가 겹치지 않는다")
    @Test
    void tossCode_unique() {

        /*
         * test, verify
         */
        assertThat(Arrays.stream(BankCode.values()).map(BankCode::getTossCode))
                .doesNotHaveDuplicates();
    }

    @DisplayName("tossCode - 금융결제원 코드에서 0 을 뗀 값과 다른 은행도 토스 문서 값을 쓴다")
    @ParameterizedTest
    @CsvSource({
            "KOOKMIN, 06",
            "IBK, 03",
            "LOCAL_NONGHYUP, 12",
            "KFCC, 45"
    })
    void tossCode_documented(BankCode bank, String expected) {

        /*
         * test, verify
         */
        assertThat(bank.getTossCode()).isEqualTo(expected);
    }
}
