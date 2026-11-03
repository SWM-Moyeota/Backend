package team.codingforest.moyeota.matching.noshow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import team.codingforest.moyeota.matching.noshow.domain.NoShowObjection;

public record NoShowObjectionRequest(
        @NotBlank(message = "이의 사유는 필수입니다")
        @Size(max = NoShowObjection.MAX_REASON_LENGTH, message = "이의 사유는 500자 이하여야 합니다")
        @Schema(description = "이의 사유(1~500자, 앞뒤 공백은 뗀다)", example = "약속 장소에 도착했는데 앱의 도착 버튼이 눌리지 않았습니다.") String reason) {
}
