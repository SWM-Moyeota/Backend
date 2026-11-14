package team.codingforest.moyeota.user.social.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import team.codingforest.moyeota.user.users.domain.enums.Gender;

import java.time.Instant;

/**
 * 소셜 회원가입 요청. 로컬 가입(UserRegisterRequest)에서 아이디·비밀번호·이메일이 빠지고 가입 대기표가 들어간다.
 * 이메일은 받지 않는다 - 소셜 서비스가 확인해 준 값을 대기표에서 꺼내 쓴다
 */
public record SocialRegisterRequest(
        @NotBlank(message = "소셜 로그인이 필요합니다.")
        @Schema(description = "POST /api/v1/auth/login/google 응답의 signupToken", example = "5d0c2a7e-...") String signupToken,

        @NotBlank(message = "휴대폰 인증이 필요합니다.")
        @Schema(description = "POST /api/v1/auth/sms/verify 응답의 verificationToken", example = "3f2b8c1e-...") String verificationToken,

        @NotBlank(message = "전화번호는 필수입니다.")
        @Pattern(regexp = "^01[016-9]-?\\d{3,4}-?\\d{4}$",
                message = "전화번호 형식이 올바르지 않습니다.")
        @Schema(description = "하이픈 유무 무관. 문자 인증을 받은 번호와 같아야 한다", example = "010-1234-5678") String phoneNumber,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 10, message = "닉네임은 2~10자여야 합니다.")
        @Schema(description = "동승자에게 보이는 이름. 한글·영문·숫자 2~10자, 중복 불가", example = "길동이") String nickname,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 20, message = "이름은 20자를 넘을 수 없습니다.")
        @Schema(description = "실명 - 동승자에게 노출되지 않음. 소셜 계정 이름을 미리 채워 주되 사용자가 고칠 수 있다", example = "홍길동") String name,

        @NotNull(message = "생년월일은 필수입니다.")
        @Past(message = "생년월일은 과거 날짜여야 합니다.")
        @Schema(description = "만 14세 미만은 가입 불가(UNDER_MINIMUM_AGE, USER113). 한국 시각(KST) 날짜로 센 만 나이이고 14번째 생일 당일부터 가입 가능",
                example = "2000-01-01T00:00:00Z") Instant birthDate,

        @NotNull(message = "성별은 필수입니다.")
        Gender gender
) {
    public SocialRegisterCommand toCommand() {
        return new SocialRegisterCommand(signupToken, verificationToken, phoneNumber, nickname, name, birthDate, gender);
    }
}
