package team.codingforest.moyeota.user.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UserLoginRequest(
        @Schema(example = "hong123") @NotBlank(message = "아이디는 필수입니다.") String loginId,
        @Schema(example = "Passw0rd!") @NotBlank(message = "비밀번호는 필수입니다.") String password)
{
    public UserLoginCommand toCommand() {
        return new UserLoginCommand(loginId, password);
    }
}
