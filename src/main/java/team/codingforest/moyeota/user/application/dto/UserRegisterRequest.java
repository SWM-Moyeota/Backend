package team.codingforest.moyeota.user.application.dto;

import jakarta.validation.constraints.*;
import team.codingforest.moyeota.user.domain.enums.Gender;

import java.time.Instant;

public record UserRegisterRequest(
        @NotBlank(message = "아이디는 필수입니다.")
        @Pattern(regexp = "^[a-z][a-z0-9_]{3,19}$",
                message = "아이디는 영소문자로 시작하는 4~20자의 영소문자, 숫자, _ 조합이어야 합니다.")
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).+$",
                message = "비밀번호는 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 20, message = "이름은 20자를 넘을 수 없습니다.")
        String name,

        @NotNull(message = "생년월일은 필수입니다.")
        @Past(message = "생년월일은 과거 날짜여야 합니다.")
        Instant birthDate,

        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(regexp = "^01[016-9]-?\\d{3,4}-?\\d{4}$",
                message = "전화번호 형식이 올바르지 않습니다.")
        String phoneNumber,

        @NotNull(message = "성별은 필수입니다.")
        Gender gender,

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자를 넘을 수 없습니다.")
        String email
) {
    public UserRegisterCommand toCommand() {
        return new UserRegisterCommand(loginId, password, name, birthDate, phoneNumber, gender, email);
    }
}
