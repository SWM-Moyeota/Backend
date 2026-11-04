package team.codingforest.moyeota.settlement.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.settlement.account.domain.BusinessType;
import team.codingforest.moyeota.settlement.account.domain.DriverPayoutAccount;
import team.codingforest.moyeota.settlement.account.domain.SellerStatus;

public record PayoutAccountResponse(
        @Schema(description = "사업자 유형") BusinessType businessType,
        @Schema(description = "은행") BankCode bankCode,
        @Schema(description = "은행 이름", example = "국민은행") String bankName,
        @Schema(description = "끝 4자리만 보이는 계좌번호", example = "********6789") String maskedAccountNumber,
        @Schema(description = "예금주") String accountHolder,
        @Schema(description = "결제사 셀러 상태. 등록이 끝나지 않았으면 null. APPROVAL_REQUIRED - 본인 인증 대기 / PARTIALLY_APPROVED - 주 1천만 원 미만 지급 가능 / KYC_REQUIRED - 추가 심사 필요 / APPROVED - 지급 가능", nullable = true) SellerStatus sellerStatus,
        @Schema(description = "지금 정산금을 받을 수 있는지") boolean payable) {

    public static PayoutAccountResponse from(DriverPayoutAccount account) {
        return new PayoutAccountResponse(account.getBusinessType(), account.getBankCode(), account.getBankCode().getDisplayName(),
                account.getMaskedAccountNumber(), account.getAccountHolder(), account.getSellerStatus(), account.canReceivePayout());
    }
}
