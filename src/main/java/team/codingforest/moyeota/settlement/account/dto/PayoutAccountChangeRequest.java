package team.codingforest.moyeota.settlement.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import team.codingforest.moyeota.common.bank.BankCode;

public record PayoutAccountChangeRequest(
        @NotNull(message = "은행은 필수입니다.")
        @Schema(example = "SHINHAN") BankCode bankCode,

        @NotBlank(message = "계좌번호는 필수입니다.")
        @Pattern(regexp = "\\d{1,14}", message = "계좌번호는 숫자만 14자리 이하로 입력해야 합니다.")
        @Schema(description = "하이픈 없이 숫자만", example = "1002003004005") String accountNumber,

        @NotBlank(message = "예금주는 필수입니다.")
        @Size(max = 50, message = "예금주는 50자 이하여야 합니다.")
        @Schema(example = "모여타택시") String accountHolder
) {
    @Override
    public String toString() {
        return "PayoutAccountChangeRequest[bankCode=" + bankCode + ", accountNumber=***, accountHolder=***]";
    }
}
