package team.codingforest.moyeota.settlement.account.domain;

import team.codingforest.moyeota.common.bank.BankCode;

/**
 * 셀러 등록 요청. 계좌번호 원문이 들어 있어 로그에 남지 않게 toString 을 가림
 *
 * @param refSellerId   우리 쪽 셀러 참조 id
 * @param businessType  사업자 유형
 * @param businessInfo  사업자 정보
 * @param bankCode      은행
 * @param accountNumber 계좌번호 원문
 * @param accountHolder 예금주
 */
public record SellerRegistration(String refSellerId, BusinessType businessType, BusinessInfo businessInfo,
                                 BankCode bankCode, String accountNumber, String accountHolder) {

    @Override
    public String toString() {
        return "SellerRegistration[refSellerId=" + refSellerId + ", businessType=" + businessType + ", businessInfo=" + businessInfo
                + ", bankCode=" + bankCode + ", accountNumber=***, accountHolder=***]";
    }
}
