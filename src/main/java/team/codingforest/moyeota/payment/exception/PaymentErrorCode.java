package team.codingforest.moyeota.payment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.common.exception.ErrorCode;

@Getter
public enum PaymentErrorCode implements ErrorCode {
    // 결제수단
    PAYMENT_METHOD_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 결제수단이 없습니다."),
    INVALID_BILLING_KEY(HttpStatus.BAD_REQUEST, "유효하지 않은 빌링키입니다."),
    BILLING_KEY_OWNER_MISMATCH(HttpStatus.FORBIDDEN, "본인 명의로 발급된 빌링키가 아닙니다."),
    UNSUPPORTED_PAYMENT_METHOD(HttpStatus.BAD_REQUEST, "지원하지 않는 결제수단입니다."),

    // 결제 그룹·결제 조회
    PAYMENT_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 결제 그룹입니다."),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 결제입니다."),
    NOT_PAYMENT_OWNER(HttpStatus.FORBIDDEN, "본인의 결제가 아닙니다."),
    NOT_GROUP_MEMBER(HttpStatus.FORBIDDEN, "해당 결제 그룹의 구성원이 아닙니다."),

    // 상태 전이
    PAYMENT_NOT_RETRYABLE(HttpStatus.CONFLICT, "실패한 결제만 다시 시도할 수 있습니다."),
    PAYMENT_NOT_READY(HttpStatus.CONFLICT, "청구 대기 상태의 결제가 아닙니다."),
    PAYMENT_NOT_PAID(HttpStatus.CONFLICT, "결제 완료 상태가 아닙니다."),
    PAYMENT_ALREADY_FINALIZED(HttpStatus.CONFLICT, "이미 종결된 결제입니다."),
    GROUP_NOT_COVERABLE(HttpStatus.CONFLICT, "대납할 수 있는 상태가 아닙니다. 모든 청구가 끝나고 실패분이 남아야 합니다."),
    COVER_ALREADY_IN_PROGRESS(HttpStatus.CONFLICT, "이미 진행 중인 대납이 있습니다."),
    PAYMENT_GROUP_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 결제 그룹이 만들어진 방입니다."),

    // 입력 검증
    INVALID_FARE(HttpStatus.BAD_REQUEST, "요금은 0보다 커야 합니다."),
    INVALID_PASSENGER_COUNT(HttpStatus.BAD_REQUEST, "탑승 인원은 1명 이상이어야 합니다."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "결제 금액이 올바르지 않습니다."),

    // 결제사(포트원)
    GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "결제사 연동에 실패했습니다. 잠시 후 다시 시도해주세요."),
    INVALID_WEBHOOK_SIGNATURE(HttpStatus.UNAUTHORIZED, "웹훅 서명이 올바르지 않습니다.");

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
