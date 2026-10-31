package team.codingforest.moyeota.settlement.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import team.codingforest.moyeota.common.exception.ErrorCode;

@Getter
public enum SettlementErrorCode implements ErrorCode {
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 정산서입니다."),

    INVALID_SETTLEMENT_COMMAND(HttpStatus.BAD_REQUEST, "정산 기록 요청이 올바르지 않습니다."),
    INVALID_TOTAL_FARE(HttpStatus.BAD_REQUEST, "운행 금액은 0보다 커야 합니다."),
    INVALID_PLATFORM_PROMOTION(HttpStatus.BAD_REQUEST, "프로모션 금액은 0 이상이어야 합니다."),

    INVALID_SETTLEMENT_STATUS(HttpStatus.CONFLICT, "현재 정산 상태에서 할 수 없는 처리입니다."),
    INVALID_PAYOUT_STATUS(HttpStatus.CONFLICT, "현재 지급 상태에서 할 수 없는 처리입니다."),
    INVALID_PAYOUT_AMOUNT(HttpStatus.CONFLICT, "지급 금액은 0보다 커야 합니다.");

    private final HttpStatus httpStatus;
    private final String message;

    SettlementErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    @Override
    public String getCode() {
        return name();
    }
}
