package team.codingforest.moyeota.driver.domain;

// TODO 예외처리 재작성
public record BankAccount(String bankName, String accountNumber) {
    public BankAccount {
        if(bankName == null || bankName.isBlank()) throw new IllegalArgumentException("은행명은 필수입니다.");

        if(accountNumber == null || accountNumber.isBlank()) throw new IllegalArgumentException("계좌번호는 필수입니다.");
    }
}
