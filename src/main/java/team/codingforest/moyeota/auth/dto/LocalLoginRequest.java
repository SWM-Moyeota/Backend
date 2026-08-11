package team.codingforest.moyeota.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LocalLoginRequest(

        @Schema(description = "로그인 아이디", example = "moyeota123")
        @NotBlank(message = "loginId must not be blank")
        String loginId,

        @Schema(description = "비밀번호", example = "moyeota1234")
        @NotBlank(message = "password must not be blank")
        String password) {
}
