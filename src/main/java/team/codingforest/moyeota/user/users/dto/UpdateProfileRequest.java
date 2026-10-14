package team.codingforest.moyeota.user.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 둘 다 선택 - null 인 필드는 건드리지 않는다 */
public record UpdateProfileRequest(
        @Size(min = 2, max = 10, message = "닉네임은 2~10자여야 합니다.")
        @Schema(description = "생략하면 유지", example = "새닉")
        String nickname,

        @Schema(description = "생략하면 유지. GET /api/v1/users/profile-images 의 key 중 하나", example = "profile/presets/03.png")
        String imageKey
) {
}
