package team.codingforest.moyeota.settlement.account.dto;

import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.settlement.account.domain.BusinessInfo;
import team.codingforest.moyeota.settlement.account.domain.BusinessType;

/**
 * 지급 계좌 등록 내용. 계좌번호 원문과 사업자 정보가 들어 있어 toString 을 가림
 *
 * @param businessType  사업자 유형
 * @param businessInfo  사업자 정보
 * @param bankCode      은행
 * @param accountNumber 계좌번호 원문
 * @param accountHolder 예금주
 */
public record PayoutAccountRegisterCommand(BusinessType businessType, BusinessInfo businessInfo, BankCode bankCode,
                                           String accountNumber, String accountHolder) {

    @Override
    public String toString() {
        return "PayoutAccountRegisterCommand[businessType=" + businessType + ", businessInfo=" + businessInfo
                + ", bankCode=" + bankCode + ", accountNumber=***, accountHolder=***]";
    }
}
