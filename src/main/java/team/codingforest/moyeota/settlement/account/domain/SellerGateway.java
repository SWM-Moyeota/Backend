package team.codingforest.moyeota.settlement.account.domain;

import team.codingforest.moyeota.common.bank.BankCode;

/**
 * 지급 받을 셀러를 결제사에 등록·수정·조회함. 계좌번호 원문과 사업자 정보는 여기로만 넘기고 우리 DB 에는 남기지 않음
 */
public interface SellerGateway {
    Seller register(SellerRegistration registration);

    Seller changeAccount(String sellerId, BankCode bankCode, String accountNumber, String accountHolder);

    Seller find(String sellerId);
}
