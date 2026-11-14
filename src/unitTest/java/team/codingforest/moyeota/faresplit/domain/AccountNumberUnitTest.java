package team.codingforest.moyeota.faresplit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.faresplit.exception.FareSplitErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class AccountNumberUnitTest {

    @DisplayName("of - 은행 앱에서 복사한 하이픈·공백을 떼고 숫자만 남긴다")
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "3333-01-2345678|3333012345678",
            "110 123 456789|110123456789",
            "' 1234567890 '|1234567890",
            "1234-5678-9012-3456|1234567890123456"})
    void of(String raw, String expected) {

        /*
         * test
         */
        AccountNumber account = AccountNumber.of(raw);

        /*
         * verify
         */
        assertThat(account.value()).isEqualTo(expected);
    }

    @DisplayName("of - 숫자가 10~16자리가 아니거나 숫자·하이픈·공백 밖의 문자가 섞이면 INVALID_ACCOUNT_NUMBER")
    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "123456789", "12345678901234567", "3333-01-234567a", "3333.01.2345678", "１２３４５６７８９０", "1234567890​"})
    @NullSource
    void of_invalid(String raw) {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> AccountNumber.of(raw));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(FareSplitErrorCode.INVALID_ACCOUNT_NUMBER);
    }

    @DisplayName("new - 정규화되지 않은 값(하이픈 포함)은 바로 만들 수 없다")
    @Test
    void new_notNormalized() {

        /*
         * test
         */
        Throwable thrown = catchThrowable(() -> new AccountNumber("3333-01-2345678"));

        /*
         * verify
         */
        assertThat(thrown).isInstanceOf(BusinessException.class);
    }

    @DisplayName("toString - 로그에 끝 4자리만 남긴다")
    @Test
    void toString_masked() {

        /*
         * test
         */
        String text = AccountNumber.of("3333012345678").toString();

        /*
         * verify
         */
        assertThat(text).doesNotContain("3333012345678").endsWith("5678]");
    }
}
