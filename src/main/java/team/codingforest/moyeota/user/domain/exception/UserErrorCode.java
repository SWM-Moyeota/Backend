package team.codingforest.moyeota.user.domain.exception;

import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.common.exception.ErrorCode;

public enum UserErrorCode implements ErrorCode {
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "USER001", "유효하지 않은 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "USER002", "만료된 토큰입니다."),
    TOKEN_TYPE_MISMATCH(HttpStatus.UNAUTHORIZED, "USER003", "토큰 용도가 올바르지 않습니다."),
    TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "USER004", "다시 로그인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "USER005", "로그인이 필요합니다."),

    LOGIN_ID_DUPLICATED(HttpStatus.CONFLICT, "USER101", "이미 존재하는 아이디입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "USER102", "아이디나 비밀번호가 다릅니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER103", "존재하지 않는 사용자입니다."),
    PHONE_NUMBER_DUPLICATED(HttpStatus.CONFLICT, "USER104", "이미 가입된 전화번호입니다."),
    INVALID_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "USER105", "전화번호 형식이 올바르지 않습니다.");

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
