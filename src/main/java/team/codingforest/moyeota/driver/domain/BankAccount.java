package team.codingforest.moyeota.driver.domain;

import team.codingforest.moyeota.common.exception.BusinessException;
import team.codingforest.moyeota.driver.domain.exception.DriverErrorCode;

public record BankAccount(String bankName, String accountNumber) {
    public BankAccount {
        if(bankName == null || bankName.isBlank()) throw new BusinessException(DriverErrorCode.INVALID_BANK_NAME);

        if(accountNumber == null || accountNumber.isBlank()) throw new BusinessException(DriverErrorCode.INVALID_ACCOUNT_NUMBER);
    }
}
