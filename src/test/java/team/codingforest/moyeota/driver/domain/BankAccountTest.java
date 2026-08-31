package team.codingforest.moyeota.driver.domain;

import org.junit.jupiter.api.Test;
import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.driver.domain.exception.DriverErrorCode;

import static org.assertj.core.api.Assertions.*;

class BankAccountTest {

    @Test
    void 은행명이_없으면_생성할_수_없다() {
        assertThatThrownBy(() -> new BankAccount(null, "123-456"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DriverErrorCode.INVALID_BANK_NAME);
    }

    @Test
    void 계좌번호가_없으면_생성할_수_없다() {
        // 은행명 코드와 뒤바뀌면 앱이 엉뚱한 입력칸에 빨간줄을 긋는다
        assertThatThrownBy(() -> new BankAccount("국민은행", ""))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DriverErrorCode.INVALID_ACCOUNT_NUMBER);
    }
}
