package team.codingforest.moyeota.payment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.common.exception.ErrorCode;

@Getter
public enum PaymentErrorCode implements ErrorCode {
    // 결제수단
    PAYMENT_METHOD_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 결제수단이 없습니다."),
    PAYMENT_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "결제수단을 등록할 사용자를 찾을 수 없습니다."),
    PAYMENT_METHOD_CONFLICT(HttpStatus.CONFLICT, "이미 사용한 빌링키이거나 다른 등록 요청과 충돌했습니다. 등록 상태를 확인 후 다시 시도해주세요."),
    INVALID_BILLING_KEY(HttpStatus.BAD_REQUEST, "유효하지 않은 빌링키입니다."),
    BILLING_KEY_OWNER_MISMATCH(HttpStatus.FORBIDDEN, "빌링키의 고객 식별자가 로그인 사용자와 일치하지 않습니다."),
    UNSUPPORTED_PAYMENT_METHOD(HttpStatus.BAD_REQUEST, "카드로 발급된 빌링키만 등록할 수 있습니다."),

    // 결제사(포트원)
    GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "결제사 연동에 실패했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String message;

    PaymentErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return name();
    }
}
