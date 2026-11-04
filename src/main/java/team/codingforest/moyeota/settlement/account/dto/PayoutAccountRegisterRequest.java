package team.codingforest.moyeota.settlement.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import team.codingforest.moyeota.common.bank.BankCode;
import team.codingforest.moyeota.settlement.account.domain.BusinessInfo;
import team.codingforest.moyeota.settlement.account.domain.BusinessType;

public record PayoutAccountRegisterRequest(
        @NotNull(message = "사업자 유형은 필수입니다.")
        @Schema(description = "CORPORATE - 법인택시 / INDIVIDUAL_BUSINESS - 개인택시(개인사업자). 지금은 CORPORATE 만 등록됨", example = "CORPORATE")
        BusinessType businessType,

        @Schema(description = "상호. 없으면 생략", example = "모여타택시", nullable = true)
        String companyName,

        @NotBlank(message = "대표자명은 필수입니다.")
        @Schema(example = "홍길동")
        String representativeName,

        @NotBlank(message = "사업자등록번호는 필수입니다.")
        @Pattern(regexp = "\\d{10}", message = "사업자등록번호는 하이픈 없이 숫자 10자리여야 합니다.")
        @Schema(example = "2208112341")
        String businessRegistrationNumber,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Schema(example = "owner@example.com")
        String email,

        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(regexp = "\\d{8,15}", message = "전화번호는 하이픈 없이 숫자 8자리 이상이어야 합니다.")
        @Schema(example = "0212345678")
        String phone,

        @NotNull(message = "은행은 필수입니다.")
        @Schema(example = "KOOKMIN")
        BankCode bankCode,

        @NotBlank(message = "계좌번호는 필수입니다.")
        @Pattern(regexp = "\\d{1,14}", message = "계좌번호는 숫자만 14자리 이하로 입력해야 합니다.")
        @Schema(description = "하이픈 없이 숫자만", example = "110123456789")
        String accountNumber,

        @NotBlank(message = "예금주는 필수입니다.")
        @Size(max = 50, message = "예금주는 50자 이하여야 합니다.")
        @Schema(example = "모여타택시")
        String accountHolder
) {
    public PayoutAccountRegisterCommand toCommand() {
        return new PayoutAccountRegisterCommand(businessType,
                new BusinessInfo(companyName, representativeName, businessRegistrationNumber, email, phone),
                bankCode, accountNumber, accountHolder);
    }

    @Override
    public String toString() {
        return "PayoutAccountRegisterRequest[businessType=" + businessType + ", bankCode=" + bankCode + ", ***]";
    }
}
