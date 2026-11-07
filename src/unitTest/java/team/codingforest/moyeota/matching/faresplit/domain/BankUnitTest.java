package team.codingforest.moyeota.matching.faresplit.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class BankUnitTest {

    @DisplayName("Bank - 코드는 fare_split.bank_code(varchar 20)에 들어가고 앱 이름·토스 이름이 비어 있지 않다")
    @ParameterizedTest
    @EnumSource(Bank.class)
    void bank(Bank bank) {

        /*
         * verify
         */
        assertThat(bank.name().length()).isLessThanOrEqualTo(20);
        assertThat(bank.displayName()).isNotBlank();
        assertThat(bank.tossName()).isNotBlank();
    }
}
