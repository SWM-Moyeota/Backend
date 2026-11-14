package team.codingforest.moyeota.user.common.exception;

import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.common.exception.ErrorCode;

public enum UserErrorCode implements ErrorCode {
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "USER001", "유효하지 않은 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "USER002", "만료된 토큰입니다."),
    TOKEN_TYPE_MISMATCH(HttpStatus.UNAUTHORIZED, "USER003", "토큰 용도가 올바르지 않습니다."),
    TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "USER004", "다시 로그인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "USER005", "로그인이 필요합니다."),
    INTERNAL_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "USER007", "내부 인증이 필요합니다."),

    LOGIN_ID_DUPLICATED(HttpStatus.CONFLICT, "USER101", "이미 존재하는 아이디입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "USER102", "아이디나 비밀번호가 다릅니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER103", "존재하지 않는 사용자입니다."),
    PHONE_NUMBER_DUPLICATED(HttpStatus.CONFLICT, "USER104", "이미 가입된 전화번호입니다."),
    INVALID_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "USER105", "전화번호 형식이 올바르지 않습니다."),
    EMPTY_FCM_TOKEN(HttpStatus.BAD_REQUEST, "USER106", "FCM 토큰이 비어 있습니다."),
    INVALID_NICKNAME(HttpStatus.BAD_REQUEST, "USER107", "닉네임은 2~10자의 한글, 영문, 숫자만 가능합니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "USER108", "이미 사용 중인 닉네임입니다."),
    REJOIN_RESTRICTED(HttpStatus.CONFLICT, "USER109", "탈퇴 후 30일 동안은 같은 번호로 다시 가입할 수 없습니다."),
    ONGOING_PARTY_EXISTS(HttpStatus.CONFLICT, "USER110", "진행 중인 동승이 있어 탈퇴할 수 없습니다. 동승이 끝난 뒤 다시 시도해 주세요."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "USER111", "비밀번호가 일치하지 않습니다."),
    INVALID_PROFILE_IMAGE(HttpStatus.BAD_REQUEST, "USER112", "제공하지 않는 프로필 이미지입니다."),
    UNDER_MINIMUM_AGE(HttpStatus.BAD_REQUEST, "USER113", "만 14세 미만은 가입할 수 없습니다."),
    PHONE_NOT_REGISTERED(HttpStatus.NOT_FOUND,"USER114","가입되지 않은 사용자입니다."),
    
    PASSWORD_RESET_VERIFICATION_FAILED(HttpStatus.BAD_REQUEST, "USER301", "인증번호를 확인하거나 다시 요청해 주세요."),
    PASSWORD_RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "USER302", "인증부터 다시 진행해 주세요."),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "USER303", "새 비밀번호와 확인 값이 다릅니다."),
    PASSWORD_POLICY_VIOLATION(HttpStatus.BAD_REQUEST, "USER304", "비밀번호 규칙을 확인해 주세요."),
    SAME_PASSWORD(HttpStatus.BAD_REQUEST, "USER305", "현재와 다른 비밀번호를 입력해 주세요."),
    PASSWORD_RESET_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "USER306", "잠시 후 다시 시도해 주세요."),
    PASSWORD_RESET_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "USER307", "지금은 인증을 진행할 수 없습니다."),

    SOCIAL_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "USER401", "구글 로그인에 실패했습니다. 다시 시도해 주세요."),
    SOCIAL_SIGNUP_EXPIRED(HttpStatus.UNAUTHORIZED, "USER402", "가입 시간이 만료되었습니다. 구글 로그인부터 다시 진행해 주세요."),
    SOCIAL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "USER403", "이미 가입된 계정입니다. 다시 로그인해 주세요."),
    SOCIAL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "USER404", "지금은 로그인을 진행할 수 없습니다. 잠시 후 다시 시도해 주세요."),

    SMS_SEND_FAILED(HttpStatus.BAD_GATEWAY, "USER201", "인증번호 발송에 실패했습니다. 잠시 후 다시 시도해 주세요."),
    SMS_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "USER202", "인증번호는 1분에 한 번만 요청할 수 있습니다."),
    SMS_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "USER203", "오늘 인증번호 요청 횟수를 초과했습니다."),
    SMS_CODE_EXPIRED(HttpStatus.BAD_REQUEST, "USER204", "인증번호가 만료되었습니다. 다시 요청해 주세요."),
    SMS_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "USER205", "인증번호가 일치하지 않습니다."),
    SMS_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "USER206", "입력 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요."),
    SMS_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "USER207", "휴대폰 인증이 필요합니다."),
    SMS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "USER208", "지금은 인증을 진행할 수 없습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String customCode;
    private final String message;

    UserErrorCode(HttpStatus httpStatus, String customCode, String message) {
        this.httpStatus = httpStatus;
        this.customCode = customCode;
        this.message = message;
    }

    @Override
    public String getCode() {
        return this.customCode;
    }

    @Override
    public String getMessage() {
        return this.message;
    }

    @Override
    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }
}
