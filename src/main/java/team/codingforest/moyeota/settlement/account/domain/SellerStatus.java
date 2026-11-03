package team.codingforest.moyeota.settlement.account.domain;

/**
 * 토스 셀러 상태. PARTIALLY_APPROVED·APPROVED 일 때만 지급할 수 있음
 */
public enum SellerStatus {
    APPROVAL_REQUIRED,
    PARTIALLY_APPROVED,
    KYC_REQUIRED,
    APPROVED
}
