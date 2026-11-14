package team.codingforest.moyeota.faresplit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import team.codingforest.moyeota.faresplit.domain.enums.Bank;

import java.util.List;
import java.util.UUID;

public record FareSplitRequest(
        @NotNull(message = "택시비는 필수입니다")
        @Positive(message = "택시비는 1원 이상이어야 합니다")
        @Schema(description = "실제로 낸 택시비(원). 1원 이상", example = "18700") Integer totalFare,
        @NotNull(message = "은행은 필수입니다")
        @Schema(description = "받을 계좌의 은행 코드", example = "KAKAO") Bank bankCode,
        @NotBlank(message = "계좌번호는 필수입니다")
        @Schema(description = "받을 계좌번호. 하이픈·공백은 떼고 숫자 10~16자리여야 한다. 정산마다 입력받고 프로필에 남기지 않는다", example = "3333-01-2345678") String accountNumber,
        @NotEmpty(message = "부담 금액은 필수입니다")
        @Valid
        @Schema(description = "탑승자마다 부담 금액. 받는 사람(방장)을 포함한 탑승자 전원이 한 번씩 있어야 하고 합이 totalFare 와 같아야 한다") List<@NotNull(message = "부담 금액 항목은 비어 있을 수 없습니다") Payment> payments) {

    public record Payment(
            @NotNull(message = "탑승자는 필수입니다")
            @Schema(description = "탑승자의 publicId") UUID userPublicId,
            @NotNull(message = "부담 금액은 필수입니다")
            @Positive(message = "부담 금액은 1원 이상이어야 합니다")
            @Schema(description = "이 사람의 부담 금액(원). 1원 이상", example = "6240") Integer amount) {
    }
}
