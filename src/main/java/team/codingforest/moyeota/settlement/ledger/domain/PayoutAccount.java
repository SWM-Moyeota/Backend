package team.codingforest.moyeota.settlement.ledger.domain;

import team.codingforest.moyeota.common.bank.BankCode;

public record PayoutAccount(BankCode bankCode, String accountNumber, String accountHolder) {
    private static final int VISIBLE_LENGTH = 4;
    private static final char MASK = '*';

    public PayoutAccount {
        if(bankCode == null || isBlank(accountNumber) || isBlank(accountHolder)) {
            throw new IllegalArgumentException("지급 계좌 정보가 부족합니다.");
        }
    }

    public static String mask(String accountNumber) {
        if(accountNumber == null) return null;

        int length = accountNumber.length();
        if(length <= VISIBLE_LENGTH) return String.valueOf(MASK).repeat(length);

        return String.valueOf(MASK).repeat(length - VISIBLE_LENGTH) + accountNumber.substring(length - VISIBLE_LENGTH);
    }

    public String maskedAccountNumber() {
        return mask(accountNumber);
    }

    @Override
    public String toString() {
        return "PayoutAccount[bankCode=" + bankCode + ", accountNumber=" + maskedAccountNumber() + ", accountHolder=***]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
