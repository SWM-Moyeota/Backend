package team.codingforest.moyeota.report.domain.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.common.exception.ErrorCode;

@Getter
public enum ReportErrorCode implements ErrorCode {
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 신고입니다."),
    REPORT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "운행 중에만 신고할 수 있습니다."),
    REPORTER_REQUIRED(HttpStatus.BAD_REQUEST, "신고자 정보가 없습니다."),
    REPORT_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 통화 여부가 확정된 신고입니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ReportErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return name();
    }
}
