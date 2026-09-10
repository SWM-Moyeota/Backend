package team.codingforest.moyeota.user.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 둘 다 선택 - null 인 필드는 건드리지 않는다 */
public record UpdateProfileRequest(@Size(min = 2, max = 10, message = "닉네임은 2~10자여야 합니다.") @Schema(description = "생략하면 유지", example = "새닉") String nickname,
                                   @Schema(description = "생략하면 유지", example = "https://cdn.moyeota.app/p/1.png") @Size(max = 500) String imageUrl) {
}
