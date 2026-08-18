package team.codingforest.moyeota.driver.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BankAccountTest {

    @Test
    void 은행명이_없으면_생성할_수_없다() {
        assertThatThrownBy(() -> new BankAccount(null, "123-456"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 계좌번호가_없으면_생성할_수_없다() {
        assertThatThrownBy(() -> new BankAccount("국민은행", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}