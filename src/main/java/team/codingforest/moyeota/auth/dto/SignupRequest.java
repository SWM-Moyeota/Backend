package team.codingforest.moyeota.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import team.codingforest.moyeota.auth.entity.enums.Gender;

import java.time.LocalDate;

/*
로컬 회원가입 요청.
이 요청 하나로 세 테이블이 만들어진다.
- user         : 본체(publicId, nickname, loginType=LOCAL, role)
- local_user   : loginId, password(해시)
- user_profile : name, birthDate, phoneNumber, email, gender
*/
public record SignupRequest(

        @Schema(description = "로그인 아이디. 영문/숫자/밑줄만", example = "moyeota123")
        @NotBlank(message = "loginId must not be blank")
        @Size(min = 4, max = 20, message = "loginId must be 4~20 characters")
        //아이디에 공백이나 특수문자가 섞이면 로그인할 때 사용자가 원인을 찾기 어렵다.
        @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "loginId must contain only letters, digits, underscore")
        String loginId,

        @Schema(description = "비밀번호. 영문+숫자 8자 이상", example = "moyeota1234")
        @NotBlank(message = "password must not be blank")
        //상한 64자는 BCrypt가 72바이트까지만 반영하기 때문에 둔다. 그 뒤는 조용히 무시되어 오해를 부른다.
        @Size(min = 8, max = 64, message = "password must be 8~64 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "password must contain letters and digits")
        String password,

        @Schema(description = "이름", example = "홍길동")
        @NotBlank(message = "name must not be blank")
        @Size(max = 20, message = "name must be 20 characters or less")
        String name,


        @Schema(description = "생년월일 (yyyy-MM-dd)", example = "2000-05-13")
        @NotNull(message = "birthDate must not be null")
        @Past(message = "birthDate must be in the past")
        LocalDate birthDate,

        /*
        전화번호는 선택 항목이다. 아예 안 보내도 가입이 된다.

        @Pattern은 null을 통과시키므로 @NotBlank만 떼면 "없어도 됨"이 된다.
        다만 빈 문자열("")은 null이 아니라서 패턴 검사를 그대로 받는다.
        프론트가 비어 있는 입력칸을 ""로 보내는 일이 흔하므로 정규식에 빈 값도 허용해두고,
        저장 직전에 UserService가 null로 바꾼다.
        (그래서 "번호 없음"이 DB에 null 한 가지 모양으로만 남는다)
        */
        @Schema(description = "전화번호. 010-XXXX-XXXX 형식. 선택 항목이라 생략 가능",
                example = "010-1234-5678", nullable = true)
        @Pattern(regexp = "^(010-\\d{4}-\\d{4})?$", message = "phoneNumber must match 010-XXXX-XXXX")
        String phoneNumber,

        @Schema(description = "이메일", example = "erase.jeong@gmail.com")
        @NotBlank(message = "email must not be blank")
        @Email(message = "email format is invalid")
        @Size(max = 100, message = "email must be 100 characters or less")
        String email,

        /*
        성별도 선택 항목이다. 안 보내면 UserService가 OTHER로 채운다.

        기본값을 여기(record)가 아니라 서비스에서 채우는 이유:
        이 record는 "요청이 형식에 맞는가"만 판단하는 자리이고,
        빈 값을 무엇으로 볼지 정하는 것은 저장하는 쪽의 규칙이라서다.
        trim이나 하이픈 제거를 서비스가 하는 것과 같은 이유다.

        MALE/FEMALE/OTHER가 아닌 문자열(예: "M")을 보내면 Jackson이 enum으로 못 바꿔
        @Valid까지 가지도 못하고 400이 난다.
        */
        @Schema(description = "성별. 생략하면 OTHER로 저장된다",
                example = "MALE", allowableValues = {"MALE", "FEMALE", "OTHER"},
                defaultValue = "OTHER", nullable = true)
        Gender gender) {
}
