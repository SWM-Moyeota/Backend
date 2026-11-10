package team.codingforest.moyeota.faresplit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import team.codingforest.moyeota.faresplit.domain.enums.Bank;

public record FareSplitRequest(
        @NotNull(message = "택시비는 필수입니다")
        @Schema(description = "실제로 낸 택시비(원). 1,000~500,000", example = "18700") Integer totalFare,
        @NotNull(message = "은행은 필수입니다")
        @Schema(description = "받을 계좌의 은행 코드", example = "KAKAO") Bank bankCode,
        @NotBlank(message = "계좌번호는 필수입니다")
        @Schema(description = "받을 계좌번호. 하이픈·공백은 떼고 숫자 10~16자리여야 한다. 정산마다 입력받고 프로필에 남기지 않는다", example = "3333-01-2345678") String accountNumber) {
}
