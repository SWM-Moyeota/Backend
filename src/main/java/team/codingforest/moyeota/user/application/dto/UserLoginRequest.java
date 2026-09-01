package team.codingforest.moyeota.user.application.dto;

import jakarta.validation.constraints.NotBlank;

public record UserLoginRequest(
        @NotBlank(message = "아이디는 필수입니다.") String loginId,
        @NotBlank(message = "비밀번호는 필수입니다.") String password)
{
    public UserLoginCommand toCommand() {
        return new UserLoginCommand(loginId, password);
    }
}
