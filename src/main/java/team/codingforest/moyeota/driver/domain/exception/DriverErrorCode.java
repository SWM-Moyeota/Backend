package team.codingforest.moyeota.driver.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.common.exception.ErrorCode;


@Getter
public enum DriverErrorCode implements ErrorCode {
    DRIVER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 기사입니다."),
    DRIVER_NOT_REGISTERED(HttpStatus.NOT_FOUND, "기사로 등록되지 않은 유저입니다."),
    DRIVER_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 기사로 등록된 유저입니다."),
    DRIVER_NOT_PENDING(HttpStatus.CONFLICT, "검증 대기 상태가 아닙니다."),
    DRIVER_EMPTY_FCM_TOKEN(HttpStatus.BAD_REQUEST, "FCM 토큰이 비어있습니다."),
    INVALID_BANK_NAME(HttpStatus.BAD_REQUEST, "은행명은 필수입니다."),
    INVALID_ACCOUNT_NUMBER(HttpStatus.BAD_REQUEST, "계좌번호는 필수입니다."),
    INVALID_PLATE_NUMBER(HttpStatus.BAD_REQUEST, "차량 번호는 필수입니다."),
    INVALID_VEHICLE_SEATS(HttpStatus.BAD_REQUEST, "좌석 수가 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    DriverErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return name();
    }
}
